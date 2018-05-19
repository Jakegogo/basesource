package com.concur.basesource.reader;

/**
 * @description: 文件类型
 * @author: Jake
 * @create: 2018-05-13 23:35
 **/
public enum ReaderType {

    EXCEL("excel"),

    JSON("json");


    private String type;
    ReaderType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public boolean matches(String type) {
        return this.type.equals(type);
    }
}
