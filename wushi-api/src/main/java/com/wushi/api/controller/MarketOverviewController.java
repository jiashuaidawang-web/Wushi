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
import com.wushi.module.mainline.engine.MainlineRecognitionEngine;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.support.EngineRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
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

    private static final List<String> MAINLINE_REQUIRED_TABLES = List.of(
            "plate_daily_snapshot",
            "capital_flow_daily_snapshot"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final CycleRecognitionEngine cycleRecognitionEngine;
    private final MainlineRecognitionEngine mainlineRecognitionEngine;
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

        EngineRequest mainlineRequest = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.MAINLINE,
                TargetType.PLATE,
                null,
                null,
                query.ruleVersion(),
                MAINLINE_REQUIRED_TABLES,
                Map.of()
        );
        var mainlineJudgement = mainlineRecognitionEngine.judge(mainlineRequest);
        var mainlineCard = judgmentBlockAssembler.toBlock(mainlineJudgement);

        List<NextWatchItemVO> nextWatchList = mergeNextWatch(cycleCard.nextWatchList(), mainlineCard.nextWatchList());
        List<DataQualityIssueVO> dataQualityIssues = mergeDataQualityIssues(cycleCard.dataQualityIssues(), mainlineCard.dataQualityIssues());

        return ApiResponse.ok(new MarketOverviewVO(
                query,
                cycleCard,
                List.of(mainlineCard),
                List.of(),
                null,
                null,
                nextWatchList,
                dataQualityIssues
        ));
    }

    private List<NextWatchItemVO> mergeNextWatch(List<NextWatchItemVO> cycleItems, List<NextWatchItemVO> mainlineItems) {
        List<NextWatchItemVO> merged = new ArrayList<>();
        merged.addAll(safeList(cycleItems));
        merged.addAll(safeList(mainlineItems));
        return merged;
    }

    private List<DataQualityIssueVO> mergeDataQualityIssues(List<DataQualityIssueVO> cycleIssues, List<DataQualityIssueVO> mainlineIssues) {
        List<DataQualityIssueVO> merged = new ArrayList<>();
        merged.addAll(safeList(cycleIssues));
        merged.addAll(safeList(mainlineIssues));
        return merged;
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }
}
