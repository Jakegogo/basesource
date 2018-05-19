package com.concur.basesource.support.spring;

import com.concur.basesource.storage.ResourceDefinition;
import com.concur.basesource.storage.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;


/**
 * 资源管理器工厂
 *
 * @author jake
 */
public class StorageManagerFactory implements FactoryBean<StorageManager>, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(StorageManagerFactory.class);

    /**
     * 资源定义列表
     */
    private List<ResourceDefinition> definitions;
    /**
     * 文件映射列表
     */
    private Map<String, Class<?>> resourceMap;
    /**
     *
     * 资源文件路径
     */
    private String resourcePath;

    public void setDefinitions(List<ResourceDefinition> definitions) {
        this.definitions = definitions;
    }

    public void setResourceMap(Map<String, Class<?>> resourceMap) {
        this.resourceMap = resourceMap;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    private StorageManager storageManager;

    @PostConstruct
    protected void initialize() {
        storageManager = this.applicationContext.getAutowireCapableBeanFactory().createBean(StorageManager.class);
        storageManager.setResourceMap(resourceMap);
        storageManager.startListeningPath(resourcePath);

        for (ResourceDefinition definition : definitions) {
            storageManager.initialize(definition);
            logger.warn("基础数据" + definition.getClz().getName() + "初始化完成");
        }
    }

    @Override
    public StorageManager getObject() throws Exception {
        return storageManager;
    }

    // 实现接口的方法

    @Override
    public Class<StorageManager> getObjectType() {
        return StorageManager.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
