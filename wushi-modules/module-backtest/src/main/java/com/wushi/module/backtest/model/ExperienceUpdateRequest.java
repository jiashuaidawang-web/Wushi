package com.wushi.module.backtest.model;

import com.wushi.common.model.ForwardValidationResult;

import java.time.LocalDate;
import java.util.List;

public record ExperienceUpdateRequest(
        LocalDate statDate,
        String ruleVersion,
        List<ForwardValidationResult> validationResults
) {
}
