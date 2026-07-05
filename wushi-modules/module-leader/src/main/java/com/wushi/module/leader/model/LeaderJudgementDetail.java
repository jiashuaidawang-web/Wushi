package com.wushi.module.leader.model;

import com.wushi.common.enums.LeaderStatus;
import com.wushi.common.enums.LeaderType;

import java.math.BigDecimal;

public record LeaderJudgementDetail(
        String stockCode,
        String stockName,
        String plateCode,
        String plateName,
        LeaderType leaderType,
        LeaderStatus leaderStatus,
        BigDecimal positionScore,
        BigDecimal popularityScore,
        BigDecimal driveScore,
        BigDecimal divergenceRepairScore,
        BigDecimal challengeRiskScore,
        String challengeStockCode,
        String challengeStockName
) {
}
