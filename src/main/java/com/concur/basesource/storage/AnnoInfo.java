package com.concur.basesource.storage;

import com.concur.basesource.anno.Index;

import java.lang.reflect.Field;

/**
 * 注解信息,用作注解配置读取
 */
public class AnnoInfo {

    private Index index;

    private Field field;

    public AnnoInfo(Index index, Field field) {
        this.index = index;
        this.field = field;
    }

    public Index getIndex() {
        return index;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public Field getField() {
        return field;
    }

    public void setField(Field field) {
        this.field = field;
    }
}
