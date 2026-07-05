package com.wushi.module.leader.model;

import java.math.BigDecimal;

public record LeaderCandidate(
        Integer candidateRank,
        String stockCode,
        String stockName,
        String plateCode,
        String plateName,
        BigDecimal candidateScore,
        String candidateReason
) {
}
