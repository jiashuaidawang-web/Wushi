package com.wushi.module.rule.model.candidate;

import com.wushi.common.enums.EngineType;

public record RuleCandidateRejectRequest(
        String ruleVersion,
        EngineType engineType,
        String rejectedBy,
        String rejectionReason
) {
}
