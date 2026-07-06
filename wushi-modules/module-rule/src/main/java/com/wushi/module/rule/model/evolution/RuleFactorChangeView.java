package com.wushi.module.rule.model.evolution;

import java.math.BigDecimal;

public record RuleFactorChangeView(
        String factorCode,
        String factorName,
        BigDecimal baseWeight,
        BigDecimal suggestedWeightDelta,
        BigDecimal candidateWeight,
        Integer sampleCount,
        Integer hitCount,
        Integer missCount,
        Integer conflictHitCount,
        BigDecimal hitRate,
        BigDecimal avgContributionScore,
        String suggestedAction,
        String changeReason
) {
}
