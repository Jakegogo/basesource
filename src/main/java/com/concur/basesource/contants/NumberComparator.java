package com.concur.basesource.contants;

import java.util.Comparator;

/**
 * 数字比较器
 */
public class NumberComparator implements Comparator<Number> {
    @Override
    public int compare(Number o1, Number o2) {
        if (o1 == o2) {
            return 0;
        }
        if (o1 == null) {
            return -1;
        }
        if (o2 == null) {
            return 1;
        }

        if (o1.doubleValue() < o2.doubleValue()) {
            return -1;
        } else if (o1.doubleValue() > o2.doubleValue()) {
            return 1;
        }
        return 0;
    }
};