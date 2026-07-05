package com.wushi.module.mainline.model;

import com.wushi.common.enums.MainlineStatus;
import com.wushi.common.model.FactorResult;

import java.math.BigDecimal;
import java.util.List;

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
        BigDecimal capitalInflow,
        List<String> satisfiedConditions,
        String unmetCondition,
        String tomorrowValidation,
        List<FactorResult> factorResults
) {
}
