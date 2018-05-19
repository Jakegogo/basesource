package com.concur.basesource.support.spring;


import java.io.File;
import java.io.IOException;
import java.util.*;

import com.concur.basesource.anno.StaticResource;
import com.concur.basesource.convertor.task.ConvertTask;
import com.concur.basesource.convertor.utils.SheetInfo;
import com.concur.basesource.convertor.utils.SheetUtils;
import com.concur.basesource.reader.*;
import com.concur.basesource.storage.FormatDefinition;
import com.concur.basesource.storage.ResourceDefinition;
import com.concur.basesource.storage.Storage;
import com.concur.unity.utils.PathUtil;
import com.concur.unity.utils.StringUtils;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.SystemPropertyUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.concur.basesource.support.spring.SchemaNames.CLASS_ATTRIBUTE_NAME;
import static com.concur.basesource.support.spring.SchemaNames.PACKAGE_ATTRIBUTE_NAME;


/**
 * 配置定义处理器
 * @author frank
 */
public class ConfigDefinitionParser extends AbstractBeanDefinitionParser {

	private static final Logger logger = LoggerFactory.getLogger(ConfigDefinitionParser.class);

	/** 默认资源匹配符 */
	protected static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";

	/** 资源搜索分析器，由它来负责检索EAO接口 */
	private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
	/** 类的元数据读取器，由它来负责读取类上的注释信息 */
	private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(this.resourcePatternResolver);

	@Override
	protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
		register(parserContext);

		FormatDefinition format = parseFormat(element);
		// 遍历资源文件夹
        Map<String, String> files = getResourceFiles(format);

		// 要创建的对象信息
		ManagedList<BeanDefinition> resources = new ManagedList<BeanDefinition>();
		ManagedMap<String, Class<?>> resourceMap = new ManagedMap<String, Class<?>>();

		// 检查XML内容
		NodeList child = element.getChildNodes();
		for (int i = 0; i < child.getLength(); i++) {
			Node node = child.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			String name = node.getLocalName();

			if (name.equals(SchemaNames.PACKAGE_ELEMENT)) {
				// 自动包扫描处理
				String packageName = ((Element) node).getAttribute(PACKAGE_ATTRIBUTE_NAME);
				Map<String, String> names = getResources(packageName);
				for (String resource : names.values()) {
					Class<?> clz = null;
					try {
						clz = Class.forName(resource);
					} catch (ClassNotFoundException e) {
						FormattingTuple message = MessageFormatter.format("无法获取的资源类[{}]", resource);
						logger.error(message.getMessage());
						throw new RuntimeException(message.getMessage(), e);
					}
					String simpleName = clz.getSimpleName();
					String filePath = files.get(simpleName);
					BeanDefinition definition = parseResource(clz, format, filePath);
					resources.add(definition);

					resourceMap.put(filePath, clz);
				}
			}

			if (name.equals(SchemaNames.CLASS_ELEMENT)) {
				// 自动类加载处理
				String className = ((Element) node).getAttribute(CLASS_ATTRIBUTE_NAME);
				Class<?> clz = null;
				try {
					clz = Class.forName(className);
				} catch (ClassNotFoundException e) {
					FormattingTuple message = MessageFormatter.format("无法获取的资源类[{}]", className);
					logger.error(message.getMessage());
					throw new RuntimeException(message.getMessage(), e);
				}
                String simpleName = clz.getSimpleName();
				String filePath = files.get(simpleName);
				BeanDefinition definition = parseResource(clz, format, filePath);
				resources.add(definition);

				resourceMap.put(filePath, clz);
			}
		}

		BeanDefinitionBuilder factory = BeanDefinitionBuilder.rootBeanDefinition(StorageManagerFactory.class);
		factory.addPropertyValue("definitions", resources);
		factory.addPropertyValue("resourceMap", resourceMap);
		String path = PathUtil.getCurrentWorkDirectory() +
				StringUtils.replaceFirstLetter(format.getLocation(), "/");
		factory.addPropertyValue("resourcePath", path);
		AbstractBeanDefinition definition = factory.getBeanDefinition();

		return definition;
	}

	/**
	 * 注册必须的环境资源
	 * @param parserContext
	 */
	private void register(ParserContext parserContext) {
		registerDefaultReader(parserContext);
		registerReaderHolder(parserContext);
		registerStaticInject(parserContext);
	}

	/**
	 * 解析 XML 中的 format 元素
	 * @param element
	 * @return
	 */
	private FormatDefinition parseFormat(Element element) {
		NodeList child = element.getChildNodes();
		for (int i = 0; i < child.getLength(); i++) {
			Node node = child.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			String name = node.getLocalName();
			if (!name.equals(SchemaNames.FORMAT_ELEMENT)) {
				continue;
			}

			Element e = (Element) node;
			String type = e.getAttribute(SchemaNames.FORMAT_ATTRIBUTE_TYPE);
			String suffix = e.getAttribute(SchemaNames.FORMAT_ATTRIBUTE_SUFFIX);
			String location = e.getAttribute(SchemaNames.FORMAT_ATTRIBUTE_LOCATION);
			if (StringUtils.isNotBlank(location) && location.endsWith(File.pathSeparator)) {
				location = StringUtils.replaceFirstLetter(location, "/");
			}
			return new FormatDefinition(location, type, suffix);
		}

		throw new RuntimeException("XML文件缺少[format]元素定义");
	}

	/**
	 * 注册 {@link StaticInjectProcessor}
	 * @param parserContext
	 */
	private void registerStaticInject(ParserContext parserContext) {
		BeanDefinitionRegistry registry = parserContext.getRegistry();
		String name = StringUtils.uncapitalize(StaticInjectProcessor.class.getSimpleName());
		BeanDefinitionBuilder factory = BeanDefinitionBuilder.rootBeanDefinition(StaticInjectProcessor.class);
		registry.registerBeanDefinition(name, factory.getBeanDefinition());
	}

	/**
	 * 注册 {@link ReaderHolder}
	 * @param parserContext
	 */
	private void registerReaderHolder(ParserContext parserContext) {
		BeanDefinitionRegistry registry = parserContext.getRegistry();
		String name = StringUtils.uncapitalize(ReaderHolder.class.getSimpleName());
		BeanDefinitionBuilder factory = BeanDefinitionBuilder.rootBeanDefinition(ReaderHolder.class);
		registry.registerBeanDefinition(name, factory.getBeanDefinition());
	}

	/**
	 * 注册默认的 {@link ResourceReader}
	 * @param parserContext
	 */
	private void registerDefaultReader(ParserContext parserContext) {
		BeanDefinitionRegistry registry = parserContext.getRegistry();

		// 注册 ExcelReader
		String name = StringUtils.uncapitalize(ExcelReader.class.getSimpleName());
		BeanDefinitionBuilder factory = BeanDefinitionBuilder.rootBeanDefinition(ExcelReader.class);
		registry.registerBeanDefinition(name, factory.getBeanDefinition());

		// 注册 JsonReader
		name = StringUtils.uncapitalize(JsonReader.class.getSimpleName());
		factory = BeanDefinitionBuilder.rootBeanDefinition(JsonReader.class);
		registry.registerBeanDefinition(name, factory.getBeanDefinition());
	}

	/**
	 * 解析资源定义
	 * @param clz 资源类
	 * @param format 格式定义
	 * @return
	 */
	private BeanDefinition parseResource(Class<?> clz, FormatDefinition format, String file) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(ResourceDefinition.class);
		builder.addConstructorArgValue(clz);
		builder.addConstructorArgValue(format);
		builder.addConstructorArgValue(file);
		return builder.getBeanDefinition();
	}

	/**
	 * 获取指定包下的静态资源对象
	 * @param packageName 包名
	 * @return
	 * @throws IOException
	 */
	private Map<String, String> getResources(String packageName) {
		try {
			// 搜索资源
			String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
					+ resolveBasePackage(packageName) + "/" + DEFAULT_RESOURCE_PATTERN;
			Resource[] resources = this.resourcePatternResolver.getResources(packageSearchPath);
			// 提取资源
			Map<String, String> result = new HashMap<String, String>();
			String name = StaticResource.class.getName();
			for (Resource resource : resources) {
				if (!resource.isReadable()) {
					continue;
				}
				// 判断是否静态资源
				MetadataReader metaReader = this.metadataReaderFactory.getMetadataReader(resource);
				AnnotationMetadata annoMeta = metaReader.getAnnotationMetadata();
				if (!annoMeta.hasAnnotation(name)) {
					continue;
				}
				ClassMetadata clzMeta = metaReader.getClassMetadata();
				String className = clzMeta.getClassName();
				result.put(StringUtils.substringAfterLast(className, "."), className);
			}

			return result;
		} catch (IOException e) {
			String message = "无法读取资源信息";
			logger.error(message, e);
			throw new RuntimeException(message, e);
		}
	}

	/**
	 * 获取指定文件夹下的静态资源文件
	 * @param format 包名
	 * @return
	 * @throws IOException
	 */
	private Map<String, String> getResourceFiles(FormatDefinition format) {
		Map<String, String> result = new HashMap<String, String>();

		String path = PathUtil.getCurrentWorkDirectory() +
				StringUtils.replaceFirstLetter(format.getLocation(), "/");
		Collection<File> files = FileUtils.listFiles(new File(path), new String[]{format.getSuffix()}, true);
		for (File file : files) {

			if (ReaderType.EXCEL.matches(format.getType())) {
				try {
                    Workbook workbook = SheetUtils.getWorkbook(file);
                    Map<String, SheetInfo> sheets = SheetUtils.listSheets(workbook, file);

                    for (Map.Entry<String, SheetInfo> entry : sheets.entrySet()) {
                        String name = entry.getKey();
                        if (result.containsKey(name)) {
                            logger.error("资源文件{}的{}表格和{}重复", file.getName(), name, result.get(name));
                        } else {
                            result.put(name, file.getPath());
                        }
                    }
				} catch (RuntimeException e) {
					logger.error("资源文件{}读取处理getResourceFiles()异常:", file.getName(), e);
				}
			}
		}
		return result;
	}

	protected String resolveBasePackage(String basePackage) {
		return ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils.resolvePlaceholders(basePackage));
	}

}
