package com.concur.basesource.storage;

import com.concur.basesource.anno.InjectBean;
import com.concur.unity.reflect.ReflectionUtility;
import com.concur.unity.utils.StringUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.FieldFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;


/**
 * 资源定义信息对象
 * 
 * @author frank
 */
public class ResourceDefinition {
	
	/** 注入属性域过滤器 */
	private final static FieldFilter INJECT_FILTER = new FieldFilter() {
		@Override
		public boolean matches(Field field) {
			return field.isAnnotationPresent(InjectBean.class);
		}
	};

	/** 资源类 */
	private final Class<?> clz;
	/** 资源路径 */
	private final String location;
	/** 资源格式 */
	private final String format;
	/** 资源的注入信息 */
	private final Set<InjectDefinition> injects = new HashSet<InjectDefinition>();
	
	/** 构造方法 */
	public ResourceDefinition(Class<?> clz, FormatDefinition format, String file) {
		this.clz = clz;
		this.format = format.getType();

		if (StringUtils.isNotBlank(file)) {
			this.location = file;
		} else {
			this.location = format.getLocation();
		}
		ReflectionUtility.doWithDeclaredFields(clz, new FieldCallback() {
			@Override
			public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
				InjectDefinition definition = new InjectDefinition(field);
				injects.add(definition);
			}
		}, INJECT_FILTER);
	}
	
	/**
	 * 获取静态属性注入定义
	 * @return
	 */
	public Set<InjectDefinition> getStaticInjects() {
		HashSet<InjectDefinition> result = new HashSet<InjectDefinition>();
		for (InjectDefinition definition : this.injects) {
			Field field = definition.getField();
			if (Modifier.isStatic(field.getModifiers())) {
				result.add(definition);
			}
		}
		return result;
	}
	
	/**
	 * 获取非静态属性注入定义
	 * @return
	 */
	public Set<InjectDefinition> getInjects() {
		HashSet<InjectDefinition> result = new HashSet<InjectDefinition>();
		for (InjectDefinition definition : this.injects) {
			Field field = definition.getField();
			if (!Modifier.isStatic(field.getModifiers())) {
				result.add(definition);
			}
		}
		return result;
	}

	/**
	 * 资源是否需要校验
	 * @return
	 */
	public boolean isNeedValidate() {
		return true;
	}
	
	// Getter and Setter ...
	
	public Class<?> getClz() {
		return clz;
	}

	public String getLocation() {
		return location;
	}

	public String getFormat() {
		return format;
	}

	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this);
	}
}
