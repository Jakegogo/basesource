package com.concur.basesource.contants;

import java.util.Comparator;

/**
 * 文本比较器
 */
public class StringComparator implements Comparator<Object> {
    @Override
    public int compare(Object o1, Object o2) {
        if (o1 == o2) {
            return 0;
        }
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) {
            return 1;
        }
        return String.valueOf(o1).compareTo(String.valueOf(o2));
    }
};