package com.concur.basesource.support.spring;

import com.concur.basesource.anno.Id;
import com.concur.basesource.anno.StaticStore;
import com.concur.basesource.storage.Storage;
import com.concur.basesource.storage.StorageManager;
import com.concur.unity.reflect.ReflectionUtility;
import com.concur.unity.utils.ConvertUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 扫描基础数据定义
 * Created by Jake on 2015/6/20.
 */
@Component
public class StaticInjectProcessor extends InstantiationAwareBeanPostProcessorAdapter {

    private static final Logger logger = LoggerFactory.getLogger(StaticInjectProcessor.class);

    @Autowired
    private StorageManager storageManager;

    /**
     * 注入类型定义
     *
     * @author frank
     */
    public enum InjectType {
        /**
         * 存储空间
         */
        STORAGE,
        /**
         * 实例
         */
        INSTANCE;
    }


    @Override
    public boolean postProcessAfterInstantiation(final Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(
                bean.getClass(),
                new ReflectionUtils.FieldCallback() {
                    @Override
                    public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                        StaticStore anno = field.getAnnotation(StaticStore.class);
                        if (anno == null) {
                            return;
                        }
                        InjectType type = checkInjectType(field);
                        switch (type) {
                            case STORAGE:
                                injectStorage(bean, field, anno);
                                break;
                            case INSTANCE:
                                injectInstance(bean, field, anno);
                                break;
                        }
                    }
                }
        );
        return super.postProcessAfterInstantiation(bean, beanName);

    }

    /**
     * 注入静态资源实例
     *
     * @param bean  被注入对象
     * @param field 注入属性
     * @param anno  注入声明
     */
    private void injectInstance(Object bean, Field field, StaticStore anno) {
        // 获取注入资源主键
        Class<?> clz = getIdType(field.getType());
        Object key = ConvertUtils.convert(anno.value(), clz);

        // 添加监听器
        @SuppressWarnings("rawtypes")
        Storage storage = storageManager.getStorage(field.getType());
        StaticObserver observer = new StaticObserver(bean, field, anno, key);
        storage.addObserver(observer);

        @SuppressWarnings("unchecked")
        Object instance = storage.get(key, false);
        if (anno.required() && instance == null) {
            FormattingTuple message = MessageFormatter.format("属性[{}]的注入值不存在", field);
            logger.debug(message.getMessage());
            throw new RuntimeException(message.getMessage());
        }
        inject(bean, field, instance);

    }

    /**
     * 获取唯一标识类型
     *
     * @param clz
     * @return
     */
    private Class<?> getIdType(Class<?> clz) {
        Field field = ReflectionUtility.getFirstDeclaredFieldWith(clz, Id.class);
        return field.getType();
    }

    /**
     * 注入存储空间对象
     *
     * @param bean  被注入对象
     * @param field 注入属性
     * @param anno  注入声明
     */
    @SuppressWarnings("rawtypes")
    private void injectStorage(Object bean, Field field, StaticStore anno) {
        Type type = field.getGenericType();
        if (!(type instanceof ParameterizedType)) {
            String message = "类型声明不正确";
            logger.debug(message);
            throw new RuntimeException(message);
        }

        Type[] types = ((ParameterizedType) type).getActualTypeArguments();
        if (!(types[1] instanceof Class)) {
            String message = "类型声明不正确";
            logger.debug(message);
            throw new RuntimeException(message);
        }

        Class clz = (Class) types[1];
        Storage storage = storageManager.getStorage(clz);

        boolean required = anno.required();
        if (required && storage == null) {
            FormattingTuple message = MessageFormatter.format("静态资源类[{}]不存在", clz);
            logger.debug(message.getMessage());
            throw new RuntimeException(message.getMessage());
        }

        inject(bean, field, storage);
    }


    /**
     * 注入属性值
     *
     * @param bean
     * @param field
     * @param value
     */
    private void inject(Object bean, Field field, Object value) {
        ReflectionUtils.makeAccessible(field);
        try {
            field.set(bean, value);
        } catch (Exception e) {
            FormattingTuple message = MessageFormatter.format("属性[{}]注入失败", field);
            logger.debug(message.getMessage());
            throw new RuntimeException(message.getMessage());
        }
    }

    /**
     * 检测注入类型
     *
     * @param field
     * @return
     */
    private InjectType checkInjectType(Field field) {
        if (field.getType().equals(Storage.class)) {
            return InjectType.STORAGE;
        }
        return InjectType.INSTANCE;
    }

}
