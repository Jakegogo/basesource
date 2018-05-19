package com.concur.basesource.storage;

/**
 * @description:
 * @author: Jake
 * @create: 2018-05-19 17:24
 **/
public enum FieldSortType {

    ASC("asc", 1),

    DESC("desc", -1);

    private final String type;
    private final int value;

    FieldSortType(String type, int value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public static FieldSortType valueFrom(String type) {
        for (FieldSortType fieldSortType : FieldSortType.values()) {
            if (fieldSortType.getType().equals(type)) {
                return fieldSortType;
            }
        }
        return FieldSortType.ASC;
    }

    public int of(int value) {
        return this.value * value;
    }

}
