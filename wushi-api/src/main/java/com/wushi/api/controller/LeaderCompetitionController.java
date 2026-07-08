package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.page.LeaderCompetitionVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.leader.engine.LeaderCompetitionEngine;
import com.wushi.module.leader.model.LeaderJudgementDetail;
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
@RequestMapping("/api/leader")
@RequiredArgsConstructor
public class LeaderCompetitionController {

    private static final List<String> REQUIRED_TABLES = List.of(
            "stock_limit_status_daily",
            "stock_daily_kline",
            "stock_limit_intraday_event",
            "plate_daily_snapshot",
            "stock_plate_relation_snapshot"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final LeaderCompetitionEngine leaderCompetitionEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;

    @GetMapping("/competition")
    public ApiResponse<LeaderCompetitionVO> competition(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) String stockName,
            @RequestParam(required = false) String plateCode,
            @RequestParam(required = false) String plateName,
            @RequestParam(required = false, defaultValue = "5") Integer candidateLimit) {
        try {
            MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
            EngineRequest request = createRequest(query, stockCode, stockName, plateCode, plateName);
            List<JudgmentBlockVO<LeaderJudgementDetail>> leaderCards = leaderCompetitionEngine
                    .judgeCandidates(request, safeLimit(candidateLimit))
                    .stream()
                    .map(judgmentBlockAssembler::toBlock)
                    .toList();
            return ApiResponse.ok(new LeaderCompetitionVO(query, leaderCards, buildCompetitionSummary(leaderCards)));
        } catch (Exception ex) {
            MarketQuery query = ApiQuerySupport.query(tradeDate, asOfDate, judgementMode, ruleVersion);
            return ApiResponse.ok(new LeaderCompetitionVO(query, List.of(), "龙头引擎暂不可用：" + ex.getMessage()));
        }
    }

    private EngineRequest createRequest(MarketQuery query, String stockCode, String stockName, String plateCode, String plateName) {
        return engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.LEADER,
                TargetType.STOCK,
                stockCode,
                stockName,
                query.ruleVersion(),
                REQUIRED_TABLES,
                requestParams(plateCode, plateName)
        );
    }

    private Map<String, Object> requestParams(String plateCode, String plateName) {
        java.util.HashMap<String, Object> params = new java.util.HashMap<>();
        if (plateCode != null && !plateCode.isBlank()) {
            params.put("plateCode", plateCode);
        }
        if (plateName != null && !plateName.isBlank()) {
            params.put("plateName", plateName);
        }
        return params;
    }

    private int safeLimit(Integer candidateLimit) {
        if (candidateLimit == null || candidateLimit <= 0) {
            return 5;
        }
        return Math.min(candidateLimit, 10);
    }

    private String buildCompetitionSummary(List<JudgmentBlockVO<LeaderJudgementDetail>> leaderCards) {
        if (leaderCards == null || leaderCards.isEmpty()) {
            return "龙头竞争暂不可用";
        }
        JudgmentBlockVO<LeaderJudgementDetail> first = leaderCards.get(0);
        if (first.detail() == null) {
            return "龙头竞争暂不可用";
        }
        String leader = first.detail().stockName() == null ? first.detail().stockCode() : first.detail().stockName();
        return "当前排名第一的龙头候选是 " + leader
                + "，类型/状态为 " + first.detail().leaderType() + "/" + first.detail().leaderStatus()
                + "。明日重点验证：" + first.detail().tomorrowValidation();
    }
}
