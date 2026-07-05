package com.wushi.module.spider.core;

import com.wushi.module.spider.enums.SpiderProviderType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class SpiderFetchRequest {

    private LocalDate tradeDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private SpiderProviderType providerType;
    private boolean forceRefresh;
    private Map<String, Object> params;
}
