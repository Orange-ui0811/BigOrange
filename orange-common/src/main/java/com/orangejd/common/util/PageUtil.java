package com.orangejd.common.util;

/**
 * 分页工具：限制单页最大条数，防止全量查询打爆内存
 */
public class PageUtil {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_PAGE_NUMBER = 10_000;

    public static int capSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    public static int safePage(int page) {
        return Math.max(1, Math.min(page, MAX_PAGE_NUMBER));
    }

    public static int offset(int page, int size) {
        return (safePage(page) - 1) * capSize(size);
    }
}
