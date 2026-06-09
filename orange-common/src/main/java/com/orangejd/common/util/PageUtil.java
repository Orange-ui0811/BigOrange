package com.orangejd.common.util;

/**
 * 分页工具：限制单页最大条数，防止全量查询打爆内存
 */
public class PageUtil {

    public static final int MAX_PAGE_SIZE = 50;

    public static int capSize(int size) {
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
