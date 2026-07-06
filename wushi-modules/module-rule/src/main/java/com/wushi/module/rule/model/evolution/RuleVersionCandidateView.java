package com.wushi.module.rule.model.evolution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RuleVersionCandidateView(
        String candidateId,
        String baseRuleVersion,
        String candidateRuleVersion,
        String engineType,
        LocalDate statStartDate,
        LocalDate statEndDate,
        String status,
        Integer sampleCount,
        Integer changedFactorCount,
        BigDecimal avgHitRate,
        BigDecimal avgContributionScore,
        String changeSummary,
        String generatedBy,
        String approvedBy,
        String approvalComment,
        LocalDateTime appliedAt,
        List<RuleFactorChangeView> factorChanges
) {
}
