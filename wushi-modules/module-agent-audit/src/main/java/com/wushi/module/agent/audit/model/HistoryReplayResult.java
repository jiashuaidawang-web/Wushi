package com.wushi.module.agent.audit.model;

import java.time.LocalDate;
import java.util.List;

public record HistoryReplayResult(
        String batchId,
        LocalDate startDate,
        LocalDate endDate,
        String ruleVersion,
        String judgementMode,
        int totalDays,
        int successDays,
        int failedDays,
        long affectedRows,
        List<HistoryReplayDayResult> days
) {
}
