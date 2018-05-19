package com.concur.basesource.storage;

import com.concur.unity.reflect.ReflectionUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.lang.reflect.Field;

/**
 * @description: 属性识别器
 * @author: Jake
 * @create: 2018/5/19 下午5:14
 **/
public class FieldGetter implements Getter {

    private static final Logger logger = LoggerFactory.getLogger(FieldGetter.class);

    private final Field field;

    public FieldGetter(Field field) {
        ReflectionUtility.makeAccessible(field);
        this.field = field;
    }

    @Override
    public Object getValue(Object object) {
        Object value;
        try {
            value = field.get(object);
        } catch (Exception e) {
            FormattingTuple message = MessageFormatter.format("标识符属性访问异常", e);
            logger.error(message.getMessage());
            throw new RuntimeException(message.getMessage());
        }
        return value;
    }

    public Field getField() {
        return field;
    }
}