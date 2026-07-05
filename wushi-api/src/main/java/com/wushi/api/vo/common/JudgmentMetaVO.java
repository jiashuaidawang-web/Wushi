package com.wushi.api.vo.common;

import com.wushi.common.enums.DataQualityLevel;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JudgmentMetaVO(
        String judgementId,
        LocalDate tradeDate,
        LocalDate asOfDate,
        JudgementMode judgementMode,
        EngineType engineType,
        TargetType targetType,
        String targetCode,
        String targetName,
        BigDecimal confidence,
        String ruleVersion,
        DataQualityLevel dataQualityLevel
) {
}
