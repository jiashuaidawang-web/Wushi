package com.wushi.module.agent.audit.model;

import com.wushi.module.rule.engine.core.EngineStepResult;

import java.time.LocalDate;
import java.util.List;

public record HistoryReplayDayResult(
        LocalDate tradeDate,
        LocalDate asOfDate,
        String batchId,
        String status,
        long affectedRows,
        String errorMessage,
        List<EngineStepResult> stepResults
) {
}
