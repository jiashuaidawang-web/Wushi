package com.wushi.module.spider.enums;

public enum PoolType {

    LIMIT_UP("LIMIT_UP", "涨停池"),
    YEST_LIMIT_UP("YEST_LIMIT_UP", "昨日涨停池"),
    STRONG("STRONG", "强势池"),
    SUB_NEW("SUB_NEW", "连板池"),
    BROKEN("BROKEN", "炸板池"),
    LIMIT_DOWN("LIMIT_DOWN", "跌停池");

    private final String code;
    private final String description;

    PoolType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
