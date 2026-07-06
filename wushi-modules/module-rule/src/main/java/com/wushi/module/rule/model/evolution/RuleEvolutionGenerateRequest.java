package com.wushi.module.rule.model.evolution;

import com.wushi.common.enums.EngineType;

import java.time.LocalDate;

public record RuleEvolutionGenerateRequest(
        EngineType engineType,
        String baseRuleVersion,
        String candidateRuleVersion,
        LocalDate statStartDate,
        LocalDate statEndDate,
        Integer minSampleCount,
        String generatedBy
) {
}
