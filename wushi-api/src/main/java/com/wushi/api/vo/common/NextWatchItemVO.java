package com.wushi.api.vo.common;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;

import java.time.LocalDate;

public record NextWatchItemVO(
        String watchId,
        String judgementId,
        LocalDate tradeDate,
        LocalDate watchDate,
        EngineType engineType,
        TargetType targetType,
        String targetCode,
        String targetName,
        String title,
        String conditionExpression,
        String expectedSignal,
        String riskSignal,
        Integer priority,
        String ruleVersion,
        WatchValidationStatus validationStatus
) {
}
