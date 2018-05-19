package com.concur.basesource.storage;

import com.concur.basesource.anno.Id;
import com.concur.basesource.anno.Index;
import com.concur.basesource.anno.Indexes;
import com.concur.unity.reflect.ReflectionUtility;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;


/**
 * 唯一标示获取实例创建器
 * @author frank
 */
public class GetterBuilder {
	
	private static final Logger logger = LoggerFactory.getLogger(GetterBuilder.class);

	/** 方法识别器 */
	private static class MethodGetter implements Getter {
		
		private final Method method;
		
		public MethodGetter(Method method) {
			ReflectionUtility.makeAccessible(method);
			this.method = method;
		}
		
		@Override
		public Object getValue(Object object) {
			Object value;
			try {
				value = method.invoke(object);
			} catch (Exception e) {
				FormattingTuple message = MessageFormatter.format("标识方法访问异常", e);
				logger.error(message.getMessage());
				throw new RuntimeException(message.getMessage());
			}
			return buildIndexKey(value);
		}
	}
	
	/**
	 * 识别信息
	 * @author frank
	 */
	private static class IdentityInfo {
		
		public final Field field;
		public final Method method;
		
		public IdentityInfo(Class<?> clz) {
			Field[] fields = ReflectionUtility.getDeclaredFieldsWith(clz, Id.class);
			if (fields.length > 1) {
				FormattingTuple message = MessageFormatter.format("类[{}]的属性唯一标识声明重复", clz);
				logger.error(message.getMessage());
				throw new RuntimeException(message.getMessage());
			}
			if (fields.length == 1) {
				this.field = fields[0];
				this.method = null;
				return;
			}
			Method[] methods = ReflectionUtility.getDeclaredGetMethodsWith(clz, Id.class);
			if (methods.length > 1) {
				FormattingTuple message = MessageFormatter.format("类[{}]的方法唯一标识声明重复", clz);
				logger.error(message.getMessage());
				throw new RuntimeException(message.getMessage());
			}
			if (methods.length == 1) {
				this.method = methods[0];
				this.field = null;
				return;
			}
			FormattingTuple message = MessageFormatter.format("类[{}]缺少唯一标识声明", clz);
			logger.error(message.getMessage());
			throw new RuntimeException(message.getMessage());
		}
		
		public boolean isField() {
			return field != null;
		}
	}

	/**
	 * 创建指定资源类的唯一标示获取实例
	 * @param clz 资源类
	 * @return
	 */
	public static Getter createIdGetter(Class<?> clz) {
		IdentityInfo info = new IdentityInfo(clz);
		Getter identifier;
		if (info.isField()) {
			identifier = new FieldGetter(info.field);
		} else {
			identifier = new MethodGetter(info.method);
		}
		return identifier;
	}

	public static String buildIndexKey(Object... indexValues){
		StringBuilder builder = new StringBuilder();
		if (indexValues != null && indexValues.length > 0) {
			return builder.append(StringUtils.arrayToDelimitedString(indexValues, "^")).toString();
		}
		return builder.toString();
	}
	
	/**
	 * 属性域索引值获取器
	 * @author frank
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static class FieldIndexGetter extends FieldGetter implements IndexGetter {
		
		private final String name;
		private final boolean unique;
		private final Comparator comparator;
		
		public FieldIndexGetter(Class<?> clz, AnnoInfo annoInfo) {
			super(annoInfo.getField());
			Index index = annoInfo.getIndex();
			this.name = index.name();
			this.unique = index.unique();

			String[] orderBy = index.orderBy();
			if (ArrayUtils.isNotEmpty(orderBy)) {
				this.comparator = new FieldSortComparator(clz, index.orderBy());
			} else {
				this.comparator = null;
			}
		}

		@Override
		public Object getValue(Object object) {
			return buildIndexKey(super.getValue(object));
		}

		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public boolean isUnique() {
			return unique;
		}

		@Override
		public Comparator getComparator() {
			return comparator;
		}

		@Override
		public boolean hasComparator() {
			return comparator != null;
		}
	}


	/**
	 * 属性域索引值获取器
	 * @author frank
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static class MultiFieldIndexGetter implements IndexGetter {

		private final String name;
		private final boolean unique;
		private Comparator comparator;
		private final List<FieldInfo> fieldInfoList;

		public MultiFieldIndexGetter(Class<?> clz, List<AnnoInfo> fields) {
			String name = null;
			boolean unique = false;
			final List<FieldInfo> fieldInfoList = new ArrayList<FieldInfo>();
			for (AnnoInfo field : fields) {
				ReflectionUtility.makeAccessible(field.getField());

				Index index = field.getIndex();
				if (name != null && !name.equals(index.name())) {
					throw new IllegalArgumentException(field.getField().getDeclaringClass().getSimpleName() +
							"的索引名不匹配:" + name + "," + index.name());
				}
				name = index.name();
				unique = index.unique();

				fieldInfoList.add(new FieldInfo(index.order(), field.getField()));

				if (this.comparator == null && ArrayUtils.isNotEmpty(index.orderBy())) {
					this.comparator = new FieldSortComparator(clz, index.orderBy());
				}
			}

			this.name = name;
			this.unique = unique;

			Collections.sort(fieldInfoList);
			this.fieldInfoList = fieldInfoList;

		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public boolean isUnique() {
			return unique;
		}

		@Override
		public Object getValue(Object obj) {
			if (obj == null) {
				return null;
			}

			List<Object> fieldValues = new ArrayList<Object>();
			for (FieldInfo fieldInfo : fieldInfoList) {
				if (fieldInfo == null || fieldInfo.field == null) {
					continue;
				}
				Object fieldValue = null;
				try {
					fieldValue = fieldInfo.field.get(obj);
				} catch (IllegalAccessException e) {
					FormattingTuple message = MessageFormatter.format("标识符属性访问异常", e);
					logger.error(message.getMessage());
					throw new RuntimeException(message.getMessage());
				}
				fieldValues.add(fieldValue);
			}

			return buildIndexKey(fieldValues.toArray());
		}

		@Override
		public Comparator getComparator() {
			return comparator;
		}

		@Override
		public boolean hasComparator() {
			return comparator != null;
		}
	}

	/**
	 * 属性信息,用作排序和比较
	 */
	static class FieldInfo implements Comparable<FieldInfo> {

		public int order;

		public Field field;

		public FieldInfo(int order, Field field) {
			this.order = order;
			this.field = field;
		}

		@Override
		public int compareTo(FieldInfo that) {
			if (this.order < that.order) {
				return -1;
			} else if (this.order > that.order) {
				return 1;
			}
			return 0;
		}
	}


	/**
	 * 方法值索引值获取器
	 * @author frank
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static class MethodIndexGetter extends MethodGetter implements IndexGetter {
		
		private final String name;
		private final boolean unique;
		private final Comparator comparator;
		
		public MethodIndexGetter(Class<?> clz, Method method) {
			super(method);
			Index index = method.getAnnotation(Index.class);
			this.name = index.name();
			this.unique = index.unique();

			String[] orderBy = index.orderBy();
			if (ArrayUtils.isNotEmpty(orderBy)) {
				this.comparator = new FieldSortComparator(clz, index.orderBy());
			} else {
				this.comparator = null;
			}
		}
		
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public boolean isUnique() {
			return unique;
		}

		@Override
		public Comparator getComparator() {
			return comparator;
		}

		@Override
		public boolean hasComparator() {
			return comparator != null;
		}
	}

	/**
	 * 创建资源索引
	 * @param clz
	 * @return
	 */
	public static Map<String, IndexGetter> createIndexGetters(Class<?> clz) {
		Map<String, List<AnnoInfo>> fieldList = getIndexAnnoMap(clz);
		Method[] methods = ReflectionUtility.getDeclaredGetMethodsWith(clz, Index.class);
		
		List<IndexGetter> getters = new ArrayList<IndexGetter>(fieldList.size() + methods.length);
		for (List<AnnoInfo> fields : fieldList.values()) {
			IndexGetter getter;
			if (fields.size() == 1) {
				getter = new FieldIndexGetter(clz, fields.get(0));
			} else {
				getter = new MultiFieldIndexGetter(clz, fields);
			}
			getters.add(getter);
		}
		for (Method method : methods) {
			IndexGetter getter = new MethodIndexGetter(clz, method);
			getters.add(getter);
		}

		Map<String, IndexGetter> result = new HashMap<String, IndexGetter>(getters.size());
		for (IndexGetter getter : getters) {
			String name = getter.getName();
			if (result.put(name, getter) != null) {
				FormattingTuple message = MessageFormatter.format("资源类[{}]的索引名[{}]重复", clz, name);
				logger.error(message.getMessage());
				throw new RuntimeException(message.getMessage());
			}
		}
		return result;
	}

	/**
	 * 获取所有注解映射
	 * @param clz Class<?>
	 * @return
	 */
	private static Map<String, List<AnnoInfo>> getIndexAnnoMap(Class<?> clz) {
		Field[] fields = ReflectionUtility.getDeclaredFieldsWith(clz, Index.class);
		Map<String, List<AnnoInfo>> result = new HashMap<String, List<AnnoInfo>>();
		for (Field field : fields) {
			Index index = field.getAnnotation(Index.class);
			String indexName = index.name();
			List<AnnoInfo> fieldValues = result.get(indexName);
			if (fieldValues == null) {
				fieldValues = new ArrayList<AnnoInfo>();
				result.put(indexName, fieldValues);
			}
			fieldValues.add(new AnnoInfo(index, field));
		}

		Field[] fieldMultis = ReflectionUtility.getDeclaredFieldsWith(clz, Indexes.class);
		for (Field field : fieldMultis) {
			Indexes indexes = field.getAnnotation(Indexes.class);
			for (Index index : indexes.value()) {
				String indexName = index.name();
				List<AnnoInfo> fieldValues = result.get(indexName);
				if (fieldValues == null) {
					fieldValues = new ArrayList<AnnoInfo>();
					result.put(indexName, fieldValues);
				}
				fieldValues.add(new AnnoInfo(index, field));
			}
		}

		return result;
	}
}
