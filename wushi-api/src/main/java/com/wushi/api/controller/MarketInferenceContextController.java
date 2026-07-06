package com.wushi.api.controller;

import com.wushi.api.assembler.MarketInferenceContextAssembler;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.MarketInferenceContextVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.RunMode;
import com.wushi.module.agent.audit.inference.DailyInferencePipeline;
import com.wushi.module.agent.audit.inference.MarketInferenceContext;
import com.wushi.module.rule.engine.core.EngineRunContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketInferenceContextController {

    private final DailyInferencePipeline dailyInferencePipeline;
    private final MarketInferenceContextAssembler marketInferenceContextAssembler;

    @GetMapping("/inference-context")
    public ApiResponse<MarketInferenceContextVO> inferenceContext(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion
    ) {
        MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
        EngineRunContext runContext = EngineRunContext.builder()
                .tradeDate(query.tradeDate())
                .asOfDate(query.asOfDate())
                .judgementMode(query.judgementMode())
                .runMode(RunMode.MANUAL)
                .ruleVersion(query.ruleVersion())
                .build();
        MarketInferenceContext context = dailyInferencePipeline.infer(runContext);
        return ApiResponse.ok(marketInferenceContextAssembler.toVO(query, context));
    }
}
