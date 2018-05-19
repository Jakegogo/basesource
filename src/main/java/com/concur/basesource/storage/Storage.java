package com.concur.basesource.storage;

import com.concur.basesource.reader.ReaderHolder;
import com.concur.basesource.reader.ResourceReader;
import com.concur.unity.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * 存储空间对象
 * @author frank
 */
public class Storage<K, V> extends Observable implements ApplicationContextAware {

	private static final Logger logger = LoggerFactory.getLogger(Storage.class);

	@Autowired
	private ReaderHolder readerHolder;

	/** 已初始化标识 */
	private boolean initialized;
	/** 资源定义 */
	private ResourceDefinition resourceDefinition;

	/**
	 * 初始化方法，仅需运行一次
	 * @param definition
	 */
	public synchronized void initialize(ResourceDefinition definition) {
		if (initialized) {
			return; // 避免重复初始化
		}

		// 设置初始化标识
		this.initialized = true;
		// 获取资源信息
		this.resourceDefinition = definition;
		this.reader = readerHolder.getReader(definition.getFormat());
		this.identifier = GetterBuilder.createIdGetter(definition.getClz());
		this.indexGetters = GetterBuilder.createIndexGetters(definition.getClz());
		// 注入静态属性
		Set<InjectDefinition> injects = definition.getStaticInjects();
		for (InjectDefinition inject : injects) {
			Field field = inject.getField();
			Object injectValue = inject.getValue(this.applicationContext);
			try {
				field.set(null, injectValue);
			} catch (Exception e) {
				FormattingTuple message = MessageFormatter.format("无法注入静态资源[{}]的[{}]属性值",
						definition.getClz().getName(), inject.getField().getName());
				logger.error(message.getMessage());
				throw new IllegalStateException(message.getMessage());
			}
		}
		// 加载静态资源
		this.reload();
	}

	/** 资源读取器 */
	private ResourceReader reader;
	/** 标识获取器 */
	private Getter identifier;
	/** 索引获取器集合 */
	private Map<String, IndexGetter> indexGetters;

	/** 主存储空间 */
	private Map<K, V> values = new HashMap<K, V>();
	/** 索引存储空间 */
	private Map<String, Map<Object, List<V>>> indexs = new HashMap<String, Map<Object, List<V>>>();
	/** 唯一值存储空间 */
	private Map<String, Map<Object, V>> uniques = new HashMap<String, Map<Object, V>>();

	/** 读取锁 */
	private final Lock readLock;
	/** 写入锁 */
	private final Lock writeLock;

	public Storage() {
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		readLock = lock.readLock();
		writeLock = lock.writeLock();
	}


	/**
	 * 获取指定键对应的静态资源实例
	 * @param key 键
	 * @return
	 */
	public V get(K key) {
		return get(key, false);
	}

	/**
	 * 获取指定键对应的静态资源实例
	 * @param key 键
	 * @param flag 不存在时是否抛出异常,true:不存在时抛出异常,false:不抛出异常返回null
	 * @return
	 */
	public V get(K key, boolean flag) {
		isReady();
		readLock.lock();
		try {
			V result = values.get(key);
			if (flag && result == null) {
				FormattingTuple message = MessageFormatter.format("标识为[{}]的静态资源[{}]不存在", key, getClz().getName());
				logger.error(message.getMessage());
				throw new IllegalStateException(message.getMessage());
			}
			return result;
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * 是否包含了指定的主键
	 * @param key
	 * @return
	 */
	public boolean containsId(K key) {
		isReady();
		readLock.lock();
		try {
			return values.containsKey(key);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * 获取全部的静态资源实例
	 * @return 返回的集合是只读的，不能进行元素的添加或移除
	 */
	public Collection<V> getAll() {
		isReady();
		readLock.lock();
		try {
			return Collections.unmodifiableCollection(values.values());
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * 获取指定的唯一索引实例
	 * @param name 唯一索引名
	 * @param value 唯一索引值
	 * @return 不存在会返回 null
	 */
	public V getUnique(String name, Object... value) {
		isReady();
		readLock.lock();
		try {
			Map<Object, V> index = uniques.get(name);
			if (index == null) {
				return null;
			}
			String indexKey = GetterBuilder.buildIndexKey(value);
			return index.get(indexKey);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * 获取指定的索引内容列表
	 * @param name 索引名
	 * @param value 索引值
	 * @return 不存在会返回{@link Collections#EMPTY_LIST}
	 */
	@SuppressWarnings("unchecked")
	public List<V> getIndex(String name, Object... value) {
		isReady();
		readLock.lock();
		try {
			Map<Object, List<V>> index = indexs.get(name);
			if (index == null) {
				return Collections.EMPTY_LIST;
			}
			String indexKey = GetterBuilder.buildIndexKey(value);
			List<V> indexList = index.get(indexKey);
			if (indexList == null) {
				return Collections.EMPTY_LIST;
			}
			return new ArrayList<V>(indexList);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * 重新加载静态资源
	 */
	@SuppressWarnings("unchecked")
	public void reload() {
		isReady();
		writeLock.lock();
		InputStream input = null;
		try {
			// 数据校验bean
			ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
			Validator validator = factory.getValidator();

			// 获取数据源
			final File file = new File(getLocation());
			input = new FileInputStream(file);
			// 获取存储空间
			Iterator<V> it = reader.read(input, getClz());
			clear();
			while (it.hasNext()) {
				V obj = it.next();
				// 注入 Spring 容器的内容
				Set<InjectDefinition> injects = resourceDefinition.getInjects();
				for (InjectDefinition inject : injects) {
					Field field = inject.getField();
					Object value = inject.getValue(applicationContext);
					try {
						field.set(obj, value);
					} catch (Exception e) {
						logger.error("设置静态对象[{}]属性[{}]时出现异常", new Object[] {
							resourceDefinition.getClz().getSimpleName(), field.getName(), e });
					}
				}
				// 静态数据是否合法的检查
				if (resourceDefinition.isNeedValidate()) {
					boolean pass = true;
					String message = "";
					try {
						// validate
						Set<ConstraintViolation<V>> violations = validator.validate(obj);
						if (violations != null && violations.size() > 0) {
							pass = false;
							StringBuffer buf = new StringBuffer();
							for(ConstraintViolation<V> violation: violations) {
								buf.append(violation.getPropertyPath().toString() + "-");
								buf.append(violation.getMessage() + "<BR>\n");
							}
							message = buf.toString();
						}
					} catch (Exception e) {
						logger.error("静态数据校验时出现异常[{}:{}]", resourceDefinition.getClz().getSimpleName(), file.getName(), e);
					} finally {
						if (!pass) {
							Object id = identifier.getValue(obj);
							FormattingTuple logMsg = MessageFormatter.arrayFormat("静态数据校验不通过[{}:{}]主键id={}:{}",
									new Object[]{resourceDefinition
									.getClz().getSimpleName(), file.getName(), id, message});
							logger.error(logMsg.getMessage());
							throw new RuntimeException(logMsg.getMessage());
						}
					}
				}

				if (put(obj) != null) {
					FormattingTuple message = MessageFormatter.format("静态数据唯一标识重复[{},{}]内容:[{}]",
							new Object[]{getClz(), file.getName(),
							JsonUtils.object2JsonString(obj)});
					logger.error(message.getMessage());
					throw new IllegalStateException(message.getMessage());
				}
			}
			// 对排序索引进行排序
			for (Entry<String, Map<Object, List<V>>> entry : indexs.entrySet()) {
				String key = entry.getKey();
				IndexGetter getter = indexGetters.get(key);
				if (getter.hasComparator()) {
					for (List<V> values : entry.getValue().values()) {
						Collections.sort(values, getter.getComparator());
					}
				}
			}
			// 通知监听器
			this.setChanged();
			this.notifyObservers();
		} catch (IOException e) {
			FormattingTuple message = MessageFormatter.format("静态资源[{}]所对应的资源文件[{}]不存在", getClz().getName(),
					getLocation());
			logger.error(message.getMessage());
			throw new IllegalStateException(message.getMessage());
		} catch (ClassCastException e) {
			FormattingTuple message = MessageFormatter.format("静态资源[{}]配置的索引内容排序器不正确", getClz().getName(), e);
			logger.error(message.getMessage());
			throw new IllegalStateException(message.getMessage(), e);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (Exception e) {
				}
			}
			writeLock.unlock();
		}
	}

	/**
	 * 检查是否已经初始化完成
	 * @return
	 */
	public boolean isInitialized() {
		return initialized;
	}

	/**
	 * 获取静态资源路径
	 * @return
	 */
	public String getLocation() {
		return resourceDefinition.getLocation();
	}

	// 内部方法

	/**
	 * 检查是否初始化就绪
	 * @throws RuntimeException 未初始化时抛出
	 */
	private void isReady() {
		if (!isInitialized()) {
			String message = "基础资源未初始化完成,暂时无法获取";
			logger.error(message);
			throw new RuntimeException(message);
		}
	}

	/**
	 * 清空全部存储空间
	 */
	private void clear() {
		values.clear();
		indexs.clear();
		uniques.clear();
	}

	private V put(V value) {
		// 唯一标识处理
		@SuppressWarnings("unchecked")
		K key = (K) identifier.getValue(value);
		if (key == null) {
			FormattingTuple message = MessageFormatter.format("静态资源[{}]主键标识属性不能为null", getClz().getName());
			logger.error(message.getMessage());
			throw new RuntimeException(message.getMessage());
		}
		V result = values.put(key, value);

		// 索引处理
		for (IndexGetter getter : indexGetters.values()) {
			String name = getter.getName();
			Object indexKey = getter.getValue(value);
			// 索引内容存储
			if (getter.isUnique()) {
				Map<Object, V> index = loadUniqueIndex(name);
				if (index.put(indexKey, value) != null) {
					FormattingTuple message = new FormattingTuple("[{}]资源的唯一索引[{}]的值[{}]重复", new Object[] {
						getClz().getName(), name, indexKey }, null);
					logger.debug(message.getMessage());
					throw new RuntimeException(message.getMessage());
				}
			} else {
				List<V> index = loadListIndex(name, indexKey);
				index.add(value);
			}
		}

		return result;
	}

	private List<V> loadListIndex(String name, Object key) {
		Map<Object, List<V>> index = loadListIndex(name);
		if (index.containsKey(key)) {
			return index.get(key);
		}

		List<V> result = new ArrayList<V>();
		index.put(key, result);
		return result;
	}

	private Map<Object, List<V>> loadListIndex(String name) {
		if (indexs.containsKey(name)) {
			return indexs.get(name);
		}

		Map<Object, List<V>> result = new HashMap<Object, List<V>>();
		indexs.put(name, result);
		return result;
	}

	private Map<Object, V> loadUniqueIndex(String name) {
		if (uniques.containsKey(name)) {
			return uniques.get(name);
		}

		Map<Object, V> result = new HashMap<Object, V>();
		uniques.put(name, result);
		return result;
	}

	@SuppressWarnings("unchecked")
	public Class<V> getClz() {
		return (Class<V>) resourceDefinition.getClz();
	}

	// 实现Spring的接口

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

}
