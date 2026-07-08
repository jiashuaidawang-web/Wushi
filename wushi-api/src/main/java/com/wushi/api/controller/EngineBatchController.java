package com.wushi.api.controller;

import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.RunMode;
import com.wushi.module.agent.audit.model.HistoryReplayRequest;
import com.wushi.module.agent.audit.model.HistoryReplayResult;
import com.wushi.module.agent.audit.service.HistoryReplayService;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineBatchOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/batch")
@RequiredArgsConstructor
public class EngineBatchController {

    private final EngineBatchOrchestrator engineBatchOrchestrator;
    private final HistoryReplayService historyReplayService;

    @PostMapping("/daily")
    public ApiResponse<List<EngineStepResult>> runDaily(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) RunMode runMode,
            @RequestParam(required = false) String ruleVersion) {
        var query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
        EngineRunContext context = EngineRunContext.builder()
                .batchId(batchId)
                .tradeDate(query.tradeDate())
                .asOfDate(query.asOfDate())
                .judgementMode(query.judgementMode())
                .runMode(runMode == null ? RunMode.DAILY : runMode)
                .ruleVersion(query.ruleVersion())
                .build();
        return ApiResponse.ok(engineBatchOrchestrator.run(context));
    }

    @PostMapping("/history-replay")
    public ApiResponse<HistoryReplayResult> runHistoryReplay(@RequestBody HistoryReplayRequest request) {
        return ApiResponse.ok(historyReplayService.replay(request));
    }
}
