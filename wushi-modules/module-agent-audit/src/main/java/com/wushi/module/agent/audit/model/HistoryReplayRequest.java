package com.wushi.module.agent.audit.model;

import com.wushi.common.enums.JudgementMode;

import java.time.LocalDate;

public record HistoryReplayRequest(
        String batchId,
        LocalDate startDate,
        LocalDate endDate,
        JudgementMode judgementMode,
        String ruleVersion,
        Boolean continueOnError,
        Integer maxDays
) {

    public JudgementMode resolvedJudgementMode() {
        return judgementMode == null ? JudgementMode.RETROSPECTIVE : judgementMode;
    }

    public String resolvedRuleVersion() {
        return ruleVersion == null || ruleVersion.isBlank() ? "v0.1.0" : ruleVersion;
    }

    public boolean resolvedContinueOnError() {
        return continueOnError == null || continueOnError;
    }

    public int resolvedMaxDays() {
        return maxDays == null || maxDays <= 0 ? 366 : maxDays;
    }
}
