package com.wushi.module.rule.model.candidate;

import com.wushi.common.enums.EngineType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RuleCandidateVersion(
        String ruleVersion,
        String sourceRuleVersion,
        EngineType engineType,
        String status,
        LocalDate candidateStatDate,
        LocalDate effectiveDate,
        String createdBy,
        String approvedBy,
        LocalDateTime approvedAt,
        String approvalRemark,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<RuleCandidateFactorChange> factorChanges
) {
}
