package com.wushi.module.similarity.model;

import java.math.BigDecimal;

public record SimilarityFactorDetail(
        String factorCode,
        String factorName,
        BigDecimal currentValue,
        BigDecimal historicalValue,
        BigDecimal similarityScore,
        BigDecimal weight
) {
}
