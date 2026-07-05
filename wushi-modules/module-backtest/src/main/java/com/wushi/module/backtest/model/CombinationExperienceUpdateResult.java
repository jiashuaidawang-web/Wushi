package com.wushi.module.backtest.model;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.ExperienceAction;

import java.math.BigDecimal;

public record CombinationExperienceUpdateResult(
        String combinationCode,
        String combinationName,
        EngineType engineType,
        Integer sampleCount,
        Integer hitCount,
        Integer missCount,
        BigDecimal hitRate,
        BigDecimal avgForwardReturn,
        BigDecimal avgDrawdown,
        ExperienceAction suggestedAction
) {
}
