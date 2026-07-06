package com.wushi.module.backtest.service;

import com.wushi.module.backtest.model.CombinationExperienceUpdateResult;
import com.wushi.module.backtest.model.FactorExperienceUpdateResult;

import java.time.LocalDate;
import java.util.List;

public interface SystemGrowthQueryService {

    List<FactorExperienceUpdateResult> factorResults(LocalDate statDate, String ruleVersion);

    List<CombinationExperienceUpdateResult> combinationResults(LocalDate statDate, String ruleVersion);

    List<String> growthLogs(LocalDate statDate);
}
