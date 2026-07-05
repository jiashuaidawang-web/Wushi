package com.wushi.module.backtest.model;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.ExperienceAction;

import java.math.BigDecimal;

public record FactorExperienceUpdateResult(
        String factorCode,
        String factorName,
        EngineType engineType,
        Integer sampleCount,
        Integer hitCount,
        Integer missCount,
        BigDecimal hitRate,
        BigDecimal avgContributionScore,
        BigDecimal suggestedWeightDelta,
        ExperienceAction suggestedAction
) {
}
