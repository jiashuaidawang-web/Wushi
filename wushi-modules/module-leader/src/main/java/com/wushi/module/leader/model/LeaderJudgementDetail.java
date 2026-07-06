package com.wushi.module.leader.model;

import com.wushi.common.enums.LeaderStatus;
import com.wushi.common.enums.LeaderType;
import com.wushi.common.model.FactorResult;

import java.math.BigDecimal;
import java.util.List;

public record LeaderJudgementDetail(
        String stockCode,
        String stockName,
        String plateCode,
        String plateName,
        Integer candidateRank,
        Integer samePlateRank,
        BigDecimal candidateScore,
        String candidateReason,
        LeaderType leaderType,
        LeaderStatus leaderStatus,
        BigDecimal positionScore,
        BigDecimal popularityScore,
        BigDecimal driveScore,
        BigDecimal divergenceRepairScore,
        BigDecimal challengeRiskScore,
        String challengeStockCode,
        String challengeStockName,
        List<FactorResult> factorResults,
        List<String> satisfiedConditions,
        String unmetCondition,
        String tomorrowValidation,
        String leaderReason,
        String leaderRisk
) {
}
