package com.wushi.api.controller;

import com.wushi.api.vo.common.MarketQuery;
import com.wushi.common.enums.JudgementMode;

import java.time.LocalDate;

final class ApiQuerySupport {

    private ApiQuerySupport() {
    }

    static MarketQuery query(LocalDate tradeDate, LocalDate asOfDate, JudgementMode judgementMode, String ruleVersion) {
        return MarketQuery.of(tradeDate, asOfDate, judgementMode, ruleVersion);
    }
}
