package com.wushi.module.backtest.model;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;

import java.time.LocalDate;

public record ForwardValidationRequest(
        String judgementId,
        String watchId,
        LocalDate tradeDate,
        LocalDate validationDate,
        Integer forwardDays,
        EngineType engineType,
        TargetType targetType,
        String targetCode,
        String targetName,
        String watchTitle,
        String conditionExpression,
        String expectedSignal,
        String riskSignal,
        String ruleVersion
) {
}
