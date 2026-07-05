package com.wushi.api.vo.common;

import com.wushi.common.enums.JudgementMode;

import java.time.LocalDate;

public record MarketQuery(
        LocalDate tradeDate,
        LocalDate asOfDate,
        JudgementMode judgementMode,
        String ruleVersion
) {
    public static MarketQuery of(LocalDate tradeDate, LocalDate asOfDate, JudgementMode judgementMode, String ruleVersion) {
        LocalDate resolvedTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        LocalDate resolvedAsOfDate = asOfDate == null ? resolvedTradeDate : asOfDate;
        JudgementMode resolvedMode = judgementMode == null ? JudgementMode.REALTIME : judgementMode;
        String resolvedRuleVersion = ruleVersion == null || ruleVersion.isBlank() ? "v0.1.0" : ruleVersion;
        return new MarketQuery(resolvedTradeDate, resolvedAsOfDate, resolvedMode, resolvedRuleVersion);
    }
}
