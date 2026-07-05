package com.wushi.module.mainline.model;

import com.wushi.common.enums.MainlineStatus;
import com.wushi.common.enums.MainlineLifecycleStage;
import com.wushi.common.model.FactorResult;

import java.math.BigDecimal;
import java.util.List;

public record MainlineJudgementDetail(
        String plateCode,
        String plateName,
        String plateType,
        Integer candidateRank,
        BigDecimal candidateScore,
        String candidateReason,
        MainlineStatus mainlineStatus,
        MainlineLifecycleStage lifecycleStage,
        String lifecycleStageName,
        String lifecycleReason,
        String lifecycleRisk,
        String lifecycleNextSignal,
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
