package com.wushi.module.rule.model.candidate;

import com.wushi.common.enums.EngineType;

import java.time.LocalDate;

public record RuleCandidateApproveRequest(
        String ruleVersion,
        EngineType engineType,
        String approvedBy,
        LocalDate effectiveDate,
        String approvalRemark
) {
}
