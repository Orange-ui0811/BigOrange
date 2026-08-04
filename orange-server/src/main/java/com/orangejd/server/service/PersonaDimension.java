package com.orangejd.server.service;

import com.orangejd.common.exception.BaseException;

import java.util.Locale;

public enum PersonaDimension {
    GOAL("当前目标"),
    INTEREST("兴趣方向"),
    SKILL("技能与经验"),
    RESOURCE("可投入资源"),
    GROWTH("成长轨迹");

    private final String label;

    PersonaDimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PersonaDimension parse(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BaseException("画像维度无效");
        }
    }
}
