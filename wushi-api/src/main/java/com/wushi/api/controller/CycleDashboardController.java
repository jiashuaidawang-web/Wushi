package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.CycleDashboardVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.JudgementResult;
import com.wushi.module.emotion.engine.CycleRecognitionEngine;
import com.wushi.module.emotion.model.CycleJudgementDetail;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.support.EngineRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cycle")
@RequiredArgsConstructor
public class CycleDashboardController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "market_breadth_daily_snapshot",
            "stock_limit_status_daily"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final CycleRecognitionEngine cycleRecognitionEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;

    @GetMapping("/dashboard")
    public ApiResponse<CycleDashboardVO> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        try {
            MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
            EngineRequest request = engineRequestFactory.create(
                    query.tradeDate(),
                    query.asOfDate(),
                    query.judgementMode(),
                    EngineType.CYCLE,
                    TargetType.MARKET,
                    "MARKET",
                    "全市场",
                    query.ruleVersion(),
                    REQUIRED_TABLES,
                    Map.of()
            );
            var judgement = cycleRecognitionEngine.judge(request);
            if (judgement == null) {
                judgement = emptyResult(query);
            }
            CycleJudgementDetail detail = judgement.getDetail();
            String pathSummary = detail == null ? "周期路径暂不可用" : detail.stageReason();
            return ApiResponse.ok(new CycleDashboardVO(
                    query,
                    judgmentBlockAssembler.toBlock(judgement),
                    pathSummary == null ? "周期路径暂不可用" : pathSummary
            ));
        } catch (Exception ex) {
            MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
            return ApiResponse.ok(new CycleDashboardVO(
                    query,
                    judgmentBlockAssembler.toBlock(emptyResult(query)),
                    "周期引擎暂不可用：" + ex.getMessage()
            ));
        }
    }

    private JudgementResult<CycleJudgementDetail> emptyResult(MarketQuery query) {
        return JudgementResult.<CycleJudgementDetail>builder()
                .judgementId("CYCLE-EMPTY-" + UUID.randomUUID())
                .tradeDate(query.tradeDate())
                .asOfDate(query.asOfDate())
                .judgementMode(query.judgementMode())
                .engineType(EngineType.CYCLE)
                .targetType(TargetType.MARKET)
                .targetCode("MARKET")
                .targetName("全市场")
                .conclusion("暂无数据")
                .ruleVersion(query.ruleVersion())
                .build();
    }
}
