package com.wushi.module.rule.model;

import com.wushi.common.enums.EngineType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RuleVersionCandidateDetail(
        String candidateId,
        String baseRuleVersion,
        String targetRuleVersion,
        EngineType engineType,
        String status,
        LocalDate statDate,
        Integer factorChangeCount,
        Integer sampleCount,
        BigDecimal totalAbsDelta,
        String reasonSummary,
        String riskSummary,
        String generatedBy,
        String approvedBy,
        String approvalComment,
        LocalDateTime approvedAt,
        LocalDateTime effectiveAt,
        List<RuleVersionCandidateFactorChange> factorChanges
) {
}
