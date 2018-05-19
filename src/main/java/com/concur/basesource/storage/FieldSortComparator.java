package com.concur.basesource.storage;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @description: 属性排序比较器
 * @author: Jake
 * @create: 2018-05-19 17:09
 **/
public class FieldSortComparator<T> implements Comparator<T> {

    private static final Logger logger = LoggerFactory.getLogger(FieldSortComparator.class);

    private List<SortInfo> sortInfos;

    public FieldSortComparator(Class<T> clazz, String[] orderBy) {
        if (ArrayUtils.isNotEmpty(orderBy)) {
            List<SortInfo> sortInfos = new ArrayList<SortInfo>();

            try {
                for (String orderField : orderBy) {
                    String[] orderValue = orderField.split(" ");
                    if (orderValue != null && orderValue.length == 2) {
                        String fieldName = orderValue[0];

                        SortInfo sortInfo = new SortInfo();
                        Field field = clazz.getDeclaredField(fieldName);
                        sortInfo.fieldGetter = new FieldGetter(field);

                        String sortType = orderValue[1];
                        sortInfo.sortType = FieldSortType.valueFrom(sortType);

                        sortInfos.add(sortInfo);
                    } else {
                        logger.warn("属性排序配置无效:{}:{}", orderBy, clazz.getSimpleName());
                    }
                }
                this.sortInfos = sortInfos;
            } catch (NoSuchFieldException e) {
                FormattingTuple message = MessageFormatter.format("属性排序比较器无法创建:{}:{}",
                        orderBy, clazz.getSimpleName());
                logger.error(message.getMessage());
                throw new RuntimeException(message.getMessage());
            }
        }
    }

    @Override
    public int compare(T o1, T o2) {
        if (CollectionUtils.isEmpty(sortInfos)) {
            return 0;
        }
        for (SortInfo sortInfo : sortInfos) {
            if (sortInfo == null) {
                continue;
            }
            int result;
            Comparable field1 = (Comparable) sortInfo.fieldGetter.getValue(o1);
            Comparable field2 = (Comparable) sortInfo.fieldGetter.getValue(o2);
            if (field1 == field2) {
                continue;
            }
            if (field1 == null) {
                return sortInfo.sortType.of(-1);
            }
            if (field1 == null) {
                return sortInfo.sortType.of(1);
            }
            if ((result = field1.compareTo(field2)) != 0) {
                return sortInfo.sortType.of(result);
            }
        }
        return 0;
    }


    static class SortInfo {

        public FieldGetter fieldGetter;

        public FieldSortType sortType;

    }
}
