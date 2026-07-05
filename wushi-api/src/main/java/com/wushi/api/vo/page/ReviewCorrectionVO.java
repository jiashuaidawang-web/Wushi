package com.wushi.api.vo.page;

import com.wushi.api.vo.common.MarketQuery;

import java.util.List;

public record ReviewCorrectionVO(
        MarketQuery query,
        List<String> correctionScopes,
        List<String> pendingReviewItems,
        String reviewSummary
) {
}
