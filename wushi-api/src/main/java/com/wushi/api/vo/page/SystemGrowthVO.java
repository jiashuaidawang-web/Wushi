package com.wushi.api.vo.page;

import com.wushi.api.vo.common.MarketQuery;
import com.wushi.module.backtest.model.CombinationExperienceUpdateResult;
import com.wushi.module.backtest.model.FactorExperienceUpdateResult;

import java.util.List;

public record SystemGrowthVO(
        MarketQuery query,
        List<FactorExperienceUpdateResult> factorResults,
        List<CombinationExperienceUpdateResult> combinationResults,
        List<String> growthLogs
) {
}
