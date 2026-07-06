package com.wushi.module.rule.model;

import java.math.BigDecimal;

public record RuleVersionCandidateFactorChange(
        String factorCode,
        String factorName,
        BigDecimal currentWeight,
        BigDecimal suggestedDelta,
        BigDecimal suggestedWeight,
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
