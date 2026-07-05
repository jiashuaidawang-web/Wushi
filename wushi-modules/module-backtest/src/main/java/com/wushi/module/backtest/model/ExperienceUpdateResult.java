package com.wushi.module.backtest.model;

import java.time.LocalDate;
import java.util.List;

public record ExperienceUpdateResult(
        LocalDate statDate,
        String ruleVersion,
        List<FactorExperienceUpdateResult> factorResults,
        List<CombinationExperienceUpdateResult> combinationResults,
        List<String> growthLogs
) {
}
