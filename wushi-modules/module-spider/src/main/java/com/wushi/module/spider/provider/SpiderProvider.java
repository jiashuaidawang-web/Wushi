package com.wushi.module.spider.provider;

import com.wushi.module.spider.enums.SpiderProviderType;

public interface SpiderProvider {

    SpiderProviderType providerType();

    default boolean supports(SpiderProviderType providerType) {
        return providerType() == providerType;
    }
}
