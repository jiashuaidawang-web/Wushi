package com.wushi.module.similarity.model;

import java.math.BigDecimal;

public record SimilarityForwardPerformance(
        Integer forwardDays,
        BigDecimal returnPct,
        BigDecimal maxDrawdownPct,
        String cycleChange,
        String mainlineChange,
        String riskChange
) {
}
