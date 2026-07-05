package com.wushi.module.rule.engine.core;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.DataQualityContext;
import com.wushi.common.model.RuleContext;

import java.time.LocalDate;
import java.util.Map;

public record EngineRequest(
        LocalDate tradeDate,
        LocalDate asOfDate,
        JudgementMode judgementMode,
        EngineType engineType,
        TargetType targetType,
        String targetCode,
        String targetName,
        String ruleVersion,
        RuleContext ruleContext,
        DataQualityContext dataQualityContext,
        Map<String, Object> params
) {
}
