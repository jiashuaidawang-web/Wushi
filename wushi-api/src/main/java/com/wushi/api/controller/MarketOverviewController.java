package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.DataQualityIssueVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.common.NextWatchItemVO;
import com.wushi.api.vo.page.MarketOverviewVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.emotion.engine.CycleRecognitionEngine;
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

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketOverviewController {

    private static final List<String> CYCLE_REQUIRED_TABLES = List.of(
            "market_breadth_daily_snapshot",
            "stock_limit_status_daily"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final CycleRecognitionEngine cycleRecognitionEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;

    @GetMapping("/overview")
    public ApiResponse<MarketOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion) {
        MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
        EngineRequest cycleRequest = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.CYCLE,
                TargetType.MARKET,
                "MARKET",
                "全市场",
                query.ruleVersion(),
                CYCLE_REQUIRED_TABLES,
                Map.of()
        );
        var cycleJudgement = cycleRecognitionEngine.judge(cycleRequest);
        var cycleCard = judgmentBlockAssembler.toBlock(cycleJudgement);
        List<NextWatchItemVO> nextWatchList = cycleCard.nextWatchList();
        List<DataQualityIssueVO> dataQualityIssues = cycleCard.dataQualityIssues();

        return ApiResponse.ok(new MarketOverviewVO(
                query,
                cycleCard,
                List.of(),
                List.of(),
                null,
                null,
                nextWatchList,
                dataQualityIssues
        ));
    }
}
