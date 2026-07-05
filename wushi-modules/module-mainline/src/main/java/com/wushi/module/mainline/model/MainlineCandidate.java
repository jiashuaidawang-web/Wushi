package com.wushi.module.mainline.model;

import java.math.BigDecimal;

public record MainlineCandidate(
        Integer candidateRank,
        String plateCode,
        String plateName,
        String plateType,
        BigDecimal candidateScore,
        String candidateReason
) {
}
