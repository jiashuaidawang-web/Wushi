package com.wushi.module.rule.model.candidate;

import com.wushi.common.enums.EngineType;

import java.time.LocalDate;

public record RuleCandidateGenerateRequest(
        LocalDate statDate,
        String sourceRuleVersion,
        EngineType engineType,
        String candidateRuleVersion,
        String createdBy,
        String description
) {
}
