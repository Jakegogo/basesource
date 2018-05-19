package com.concur.basesource.storage;

import com.concur.basesource.convertor.files.monitor.FileAlterationListener;
import com.concur.basesource.convertor.files.monitor.FileAlterationMonitor;
import com.concur.basesource.convertor.files.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 资源管理器
 * @author frank
 */
@SuppressWarnings("rawtypes")
public class StorageManager implements ApplicationContextAware, FileAlterationListener {
	
	private static final Logger logger = LoggerFactory.getLogger(StorageManager.class);
	
	/** 静态类资源定义 */
	private ConcurrentHashMap<Class, ResourceDefinition> definitions = 
		new ConcurrentHashMap<Class, ResourceDefinition>();
	/** 资源存储空间 */
	private ConcurrentHashMap<Class<?>, Storage<?, ?>> storages = 
		new ConcurrentHashMap<Class<?>, Storage<?,?>>();
	/** 文件列表映射 */
	private Map<String, Class<?>> resourceMap = new HashMap<String, Class<?>>();
	/** 资源文件路径 */
	private String resourcePath;
	/** 文件变更监听器 */
	private FileAlterationMonitor fileAlterationMonitor;

	/**
	 * 初始化静态类资源
	 * @param definition 资源定义
	 */
	public void initialize(ResourceDefinition definition) {
		Class<?> clz = definition.getClz();
		if (definitions.putIfAbsent(clz, definition) != null) {
			ResourceDefinition prev = definitions.get(clz);
			FormattingTuple message = MessageFormatter.format("类[{}]的资源定义[{}]已经存在", clz, prev);
			logger.error(message.getMessage());
			throw new RuntimeException(message.getMessage());
		}
		initializeStorage(clz);
	}
	
	/**
	 * 重新加载静态类资源
	 * @param clz 要重新加载的类资源
	 */
	public void reload(Class<?> clz) {
		ResourceDefinition definition = definitions.get(clz);
		if (definition == null) {
			FormattingTuple message = MessageFormatter.format("类[{}]的资源定义不存在", clz);
			logger.error(message.getMessage());
			throw new RuntimeException(message.getMessage());
		}

		logger.warn("正在重新加载静态资源文件:{}:{}", clz.getSimpleName(), definition.getLocation());
		Storage<?, ?> storage = getStorage(clz);
		storage.reload();
	}

	/**
	 * 监听文件变化
	 * @param resourcePath 监听路径
	 */
	public void startListeningPath(String resourcePath) {
		this.resourcePath = resourcePath;

		File path = new File(resourcePath);
		/**
		 * 注册文件变化监听器
		 * @param path
		 */
		if (!path.isDirectory()) {
			return;
		}

		/** 文件更新监视器 */
		this.fileAlterationMonitor = new FileAlterationMonitor(3 * 1000);
		try {
			this.fileAlterationMonitor.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 清理文件监听器
		for (FileAlterationObserver observer : this.fileAlterationMonitor.getObservers()) {
			this.fileAlterationMonitor.removeObserver(observer);
		}

		FileAlterationObserver observer = new FileAlterationObserver(path, false);
		try {
			observer.initialize();
			this.fileAlterationMonitor.addObserver(observer);
			observer.addListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T getResource(Object key, Class<T> clz) {
		Storage storage = getStorage(clz);
		return (T) storage.get(key, false);
	}
	
	/**
	 * 获取指定类资源的存储空间
	 * @param clz 类实例
	 * @return
	 */
	public Storage<?, ?> getStorage(Class clz) {
		if (storages.containsKey(clz)) {
			return storages.get(clz);
		}
		return initializeStorage(clz);
	}
	
	/**
	 * 获取全部的存储空间对象数组
	 * @return
	 */
	public Storage<?, ?>[] listStorages() {
		List<Storage<?, ?>> storages = new ArrayList<Storage<?, ?>>();
		for (Storage<?, ?> storage : this.storages.values()) {
			storages.add(storage);
		}
		return storages.toArray(new Storage[storages.size()]);
	}

	/**
	 * 初始化类资源的存储空间
	 * @param clz 类实例
	 * @return
	 */
	private Storage initializeStorage(Class clz) {
		ResourceDefinition definition = this.definitions.get(clz);
		if (definition == null) {
			FormattingTuple message = MessageFormatter.format("静态资源[{}]的信息定义不存在，可能是配置缺失", clz.getSimpleName());
			logger.error(message.getMessage());
			throw new IllegalStateException(message.getMessage());
		}
		AutowireCapableBeanFactory beanFactory = this.applicationContext.getAutowireCapableBeanFactory();
		Storage storage = beanFactory.createBean(Storage.class);
		
		Storage prev = storages.putIfAbsent(clz, storage);
		if (prev == null) {
			storage.initialize(definition);
		}
		return prev == null ? storage : prev;
	}

	// 实现接口的方法
	
	private ApplicationContext applicationContext;
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}


	/**
	 * 清空索引基础数据定义
	 */
	public void clear() {
		this.storages.clear();
	}

	public Map<String, Class<?>> getResourceMap() {
		return resourceMap;
	}

	public void setResourceMap(Map<String, Class<?>> resourceMap) {
		this.resourceMap = resourceMap;
	}

	public String getResourcePath() {
		return resourcePath;
	}

	@Override
	public void onStart(FileAlterationObserver observer) {

	}

	@Override
	public void onDirectoryCreate(File directory) {

	}

	@Override
	public void onDirectoryChange(File directory) {

	}

	@Override
	public void onDirectoryDelete(File directory) {

	}

	@Override
	public void onFileCreate(File file) {

	}

	@Override
	public void onFileChange(File file) {
		logger.warn("监听到静态资源文件更改:{}", file.getName());
		Class<?> cls = resourceMap.get(file.getAbsolutePath());
		if (cls != null) {
			this.reload(cls);
		}
	}

	@Override
	public void onFileDelete(File file) {

	}

	@Override
	public void onStop(FileAlterationObserver observer) {

	}
}
