package com.wushi.api.vo.page;

import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.similarity.model.HistoricalSimilarityMatch;

import java.util.List;

public record HistoricalSimilarityVO(
        MarketQuery query,
        List<HistoricalSimilarityMatch> matches,
        String similaritySummary
) {
}
