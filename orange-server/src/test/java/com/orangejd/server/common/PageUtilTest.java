package com.orangejd.server.common;

import com.orangejd.common.util.PageUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageUtilTest {

    @Test
    void clampsUnsafePageInputs() {
        assertEquals(1, PageUtil.capSize(-5));
        assertEquals(PageUtil.MAX_PAGE_SIZE, PageUtil.capSize(500));
        assertEquals(1, PageUtil.safePage(-10));
        assertEquals(PageUtil.MAX_PAGE_NUMBER, PageUtil.safePage(Integer.MAX_VALUE));
        assertEquals(0, PageUtil.offset(-1, -1));
        assertEquals((PageUtil.MAX_PAGE_NUMBER - 1) * PageUtil.MAX_PAGE_SIZE,
                PageUtil.offset(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
