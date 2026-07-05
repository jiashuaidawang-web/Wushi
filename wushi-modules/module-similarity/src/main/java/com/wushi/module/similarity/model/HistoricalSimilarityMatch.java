package com.wushi.module.similarity.model;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.SimilarityMatchType;
import com.wushi.common.enums.TargetType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HistoricalSimilarityMatch(
        String matchId,
        LocalDate tradeDate,
        EngineType engineType,
        SimilarityMatchType matchType,
        TargetType targetType,
        String targetCode,
        String targetName,
        LocalDate similarTradeDate,
        String similarTargetCode,
        String similarTargetName,
        BigDecimal similarityScore,
        String forwardSummary,
        List<SimilarityFactorDetail> factorDetails,
        List<SimilarityForwardPerformance> forwardPerformances
) {
}
