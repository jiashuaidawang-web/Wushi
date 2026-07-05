package com.wushi.module.rule.support;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.rule.engine.core.EngineRequest;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

public interface EngineRequestFactory {

    EngineRequest create(
            LocalDate tradeDate,
            LocalDate asOfDate,
            JudgementMode judgementMode,
            EngineType engineType,
            TargetType targetType,
            String targetCode,
            String targetName,
            String requestedRuleVersion,
            Collection<String> requiredTables,
            Map<String, Object> params
    );
}
