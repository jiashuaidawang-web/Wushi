package com.wushi.module.mainline.model;

import com.wushi.common.enums.MainlineStatus;

import java.math.BigDecimal;

public record MainlineJudgementDetail(
        String plateCode,
        String plateName,
        String plateType,
        MainlineStatus mainlineStatus,
        BigDecimal strengthScore,
        BigDecimal continuityScore,
        BigDecimal ladderIntegrityScore,
        BigDecimal leaderQualityScore,
        BigDecimal middleArmySupportScore,
        BigDecimal rearRiskScore,
        String unmetCondition,
        String tomorrowValidation
) {
}
