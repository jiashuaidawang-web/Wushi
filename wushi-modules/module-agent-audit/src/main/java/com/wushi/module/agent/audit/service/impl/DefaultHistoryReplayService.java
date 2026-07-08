package com.wushi.module.agent.audit.service.impl;

import com.wushi.common.enums.RunMode;
import com.wushi.module.agent.audit.model.HistoryReplayDayResult;
import com.wushi.module.agent.audit.model.HistoryReplayRequest;
import com.wushi.module.agent.audit.model.HistoryReplayResult;
import com.wushi.module.agent.audit.service.HistoryReplayService;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineBatchOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultHistoryReplayService implements HistoryReplayService {

    private static final int HARD_MAX_DAYS = 3660;

    private final EngineBatchOrchestrator engineBatchOrchestrator;

    @Override
    public HistoryReplayResult replay(HistoryReplayRequest request) {
        validate(request);
        String rootBatchId = rootBatchId(request.batchId());
        int maxDays = Math.min(request.resolvedMaxDays(), HARD_MAX_DAYS);
        List<HistoryReplayDayResult> days = new ArrayList<>();

        LocalDate current = request.startDate();
        int dayCount = 0;
        while (!current.isAfter(request.endDate())) {
            dayCount++;
            if (dayCount > maxDays) {
                throw new IllegalArgumentException("历史回放区间超过最大天数限制: " + maxDays);
            }
            days.add(replayOneDay(request, rootBatchId, current));
            HistoryReplayDayResult last = days.get(days.size() - 1);
            if (!request.resolvedContinueOnError() && !"SUCCESS".equals(last.status())) {
                break;
            }
            current = current.plusDays(1);
        }

        int successDays = (int) days.stream().filter(day -> "SUCCESS".equals(day.status())).count();
        int failedDays = days.size() - successDays;
        long affectedRows = days.stream().mapToLong(HistoryReplayDayResult::affectedRows).sum();
        return new HistoryReplayResult(
                rootBatchId,
                request.startDate(),
                request.endDate(),
                request.resolvedRuleVersion(),
                request.resolvedJudgementMode().name(),
                days.size(),
                successDays,
                failedDays,
                affectedRows,
                days
        );
    }

    private HistoryReplayDayResult replayOneDay(HistoryReplayRequest request, String rootBatchId, LocalDate tradeDate) {
        String dailyBatchId = rootBatchId + "-" + tradeDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        EngineRunContext context = EngineRunContext.builder()
                .batchId(dailyBatchId)
                .tradeDate(tradeDate)
                .asOfDate(tradeDate)
                .judgementMode(request.resolvedJudgementMode())
                .runMode(RunMode.HISTORY_REPLAY)
                .ruleVersion(request.resolvedRuleVersion())
                .build();
        try {
            List<EngineStepResult> results = engineBatchOrchestrator.run(context);
            boolean success = results.stream().allMatch(EngineStepResult::isSuccess);
            long affectedRows = results.stream().mapToLong(EngineStepResult::getAffectedRows).sum();
            return new HistoryReplayDayResult(
                    tradeDate,
                    tradeDate,
                    dailyBatchId,
                    success ? "SUCCESS" : "FAILED",
                    affectedRows,
                    success ? null : firstFailure(results),
                    results
            );
        } catch (Exception exception) {
            return new HistoryReplayDayResult(
                    tradeDate,
                    tradeDate,
                    dailyBatchId,
                    "FAILED",
                    0,
                    exception.getMessage(),
                    List.of()
            );
        }
    }

    private void validate(HistoryReplayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("历史回放请求不能为空");
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new IllegalArgumentException("startDate 和 endDate 不能为空");
        }
        if (request.startDate().isAfter(request.endDate())) {
            throw new IllegalArgumentException("startDate 不能晚于 endDate");
        }
        long days = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (days > HARD_MAX_DAYS) {
            throw new IllegalArgumentException("历史回放区间过大，最多允许 " + HARD_MAX_DAYS + " 天");
        }
    }

    private String rootBatchId(String requestBatchId) {
        if (requestBatchId != null && !requestBatchId.isBlank()) {
            return requestBatchId;
        }
        return "HISTORY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String firstFailure(List<EngineStepResult> results) {
        return results.stream()
                .filter(result -> !result.isSuccess())
                .map(EngineStepResult::getMessage)
                .findFirst()
                .orElse("历史回放日批次未完全成功");
    }
}
