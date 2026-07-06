package com.wushi.module.rule.model.candidate;

import java.math.BigDecimal;

public record RuleCandidateFactorChange(
        String factorCode,
        BigDecimal sourceWeight,
        BigDecimal candidateWeight,
        BigDecimal suggestedWeightDelta,
        Integer sampleCount,
        BigDecimal hitRate,
        BigDecimal avgContributionScore,
        Integer enabled
) {
}
