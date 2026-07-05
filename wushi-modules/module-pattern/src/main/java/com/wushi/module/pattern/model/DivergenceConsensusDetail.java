package com.wushi.module.pattern.model;

import com.wushi.common.enums.DivergenceConsensusState;
import com.wushi.common.enums.TargetType;

import java.math.BigDecimal;

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
        String confirmationSignal,
        String failureSignal
) {
}
