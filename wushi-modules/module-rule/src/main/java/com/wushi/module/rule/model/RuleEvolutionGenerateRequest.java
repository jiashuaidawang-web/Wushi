package com.wushi.module.rule.model;

import com.wushi.common.enums.EngineType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RuleEvolutionGenerateRequest(
        LocalDate statDate,
        String baseRuleVersion,
        List<EngineType> engineTypes,
        Integer minSampleCount,
        BigDecimal maxAbsDeltaPerFactor,
        String generatedBy
) {
    public int resolvedMinSampleCount() {
        return minSampleCount == null || minSampleCount < 1 ? 3 : minSampleCount;
    }

    public BigDecimal resolvedMaxAbsDeltaPerFactor() {
        return maxAbsDeltaPerFactor == null || maxAbsDeltaPerFactor.compareTo(BigDecimal.ZERO) <= 0
                ? new BigDecimal("0.1500")
                : maxAbsDeltaPerFactor;
    }

    public String resolvedGeneratedBy() {
        return generatedBy == null || generatedBy.isBlank() ? "system" : generatedBy;
    }
}
