package com.wushi.module.pattern.model;

import com.wushi.common.enums.DivergenceConsensusState;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.FactorResult;

import java.math.BigDecimal;
import java.util.List;

public record DivergenceConsensusDetail(
        TargetType targetType,
        String targetCode,
        String targetName,
        DivergenceConsensusState state,
        BigDecimal divergenceScore,
        BigDecimal consensusScore,
        BigDecimal refillQualityScore,
        BigDecimal brokenLimitRiskScore,
        BigDecimal rearFeedbackScore,
        BigDecimal turnoverAcceptanceScore,
        BigDecimal shrinkAccelerationScore,
        BigDecimal highPositionFeedbackScore,
        String confirmationSignal,
        String failureSignal,
        List<FactorResult> factorResults,
        List<String> satisfiedConditions,
        String unmetCondition,
        String tomorrowValidation,
        String patternReason,
        String patternRisk
) {
}
