package com.wushi.api.controller;

import com.wushi.api.assembler.JudgmentBlockAssembler;
import com.wushi.api.vo.common.DataQualityIssueVO;
import com.wushi.api.vo.common.JudgmentBlockVO;
import com.wushi.api.vo.common.MarketQuery;
import com.wushi.api.vo.common.NextWatchItemVO;
import com.wushi.api.vo.page.MarketOverviewVO;
import com.wushi.common.api.ApiResponse;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.module.emotion.engine.CycleRecognitionEngine;
import com.wushi.module.leader.engine.LeaderCompetitionEngine;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.mainline.engine.MainlineRecognitionEngine;
import com.wushi.module.mainline.model.MainlineCandidate;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.mainline.service.MainlineCandidateSelector;
import com.wushi.module.pattern.engine.DivergenceConsensusEngine;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
import com.wushi.module.risk.engine.RiskRadarEngine;
import com.wushi.module.risk.model.RiskRadarDetail;
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

    private static final List<String> LEADER_REQUIRED_TABLES = List.of(
            "stock_limit_status_daily",
            "stock_daily_kline",
            "stock_limit_intraday_event",
            "plate_daily_snapshot",
            "stock_plate_relation_snapshot"
    );

    private static final List<String> DIVERGENCE_REQUIRED_TABLES = List.of(
            "stock_limit_status_daily",
            "stock_limit_intraday_event",
            "stock_daily_kline",
            "plate_daily_snapshot",
            "stock_plate_relation_snapshot",
            "high_position_feedback_daily"
    );

    private static final List<String> RISK_REQUIRED_TABLES = List.of(
            "high_position_feedback_daily",
            "stock_limit_status_daily",
            "market_breadth_daily_snapshot",
            "plate_daily_snapshot"
    );

    private final EngineRequestFactory engineRequestFactory;
    private final CycleRecognitionEngine cycleRecognitionEngine;
    private final MainlineRecognitionEngine mainlineRecognitionEngine;
    private final MainlineCandidateSelector mainlineCandidateSelector;
    private final LeaderCompetitionEngine leaderCompetitionEngine;
    private final DivergenceConsensusEngine divergenceConsensusEngine;
    private final RiskRadarEngine riskRadarEngine;
    private final JudgmentBlockAssembler judgmentBlockAssembler;

    @GetMapping("/overview")
    public ApiResponse<MarketOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(required = false) JudgementMode judgementMode,
            @RequestParam(required = false) String ruleVersion,
            @RequestParam(required = false, defaultValue = "3") Integer leaderCandidateLimit) {
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

        List<JudgmentBlockVO<MainlineJudgementDetail>> mainlineCards = buildMainlineCards(query);
        List<JudgmentBlockVO<LeaderJudgementDetail>> leaderCards = buildLeaderCards(query, leaderCandidateLimit);
        JudgmentBlockVO<DivergenceConsensusDetail> divergenceCard = buildDivergenceCard(query);
        JudgmentBlockVO<RiskRadarDetail> riskCard = buildRiskCard(query);
        List<NextWatchItemVO> nextWatchList = new ArrayList<>(safeList(cycleCard.nextWatchList()));
        List<DataQualityIssueVO> dataQualityIssues = new ArrayList<>(safeList(cycleCard.dataQualityIssues()));
        mainlineCards.forEach(card -> {
            nextWatchList.addAll(safeList(card.nextWatchList()));
            dataQualityIssues.addAll(safeList(card.dataQualityIssues()));
        });
        leaderCards.forEach(card -> {
            nextWatchList.addAll(safeList(card.nextWatchList()));
            dataQualityIssues.addAll(safeList(card.dataQualityIssues()));
        });
        nextWatchList.addAll(safeList(divergenceCard.nextWatchList()));
        nextWatchList.addAll(safeList(riskCard.nextWatchList()));
        dataQualityIssues.addAll(safeList(divergenceCard.dataQualityIssues()));
        dataQualityIssues.addAll(safeList(riskCard.dataQualityIssues()));

        return ApiResponse.ok(new MarketOverviewVO(
                query,
                cycleCard,
                mainlineCards,
                leaderCards,
                divergenceCard,
                riskCard,
                nextWatchList,
                dataQualityIssues
        ));
    }

    private List<JudgmentBlockVO<MainlineJudgementDetail>> buildMainlineCards(MarketQuery query) {
        List<MainlineCandidate> candidates = mainlineCandidateSelector.selectCandidates(query.tradeDate(), 3);
        if (candidates.isEmpty()) {
            EngineRequest request = createMainlineRequest(query, null, null, Map.of());
            return List.of(judgmentBlockAssembler.toBlock(mainlineRecognitionEngine.judge(request)));
        }
        return candidates.stream()
                .map(candidate -> {
                    EngineRequest request = createMainlineRequest(
                            query,
                            candidate.plateCode(),
                            candidate.plateName(),
                            candidateParams(candidate)
                    );
                    return judgmentBlockAssembler.toBlock(mainlineRecognitionEngine.judge(request));
                })
                .toList();
    }

    private List<JudgmentBlockVO<LeaderJudgementDetail>> buildLeaderCards(MarketQuery query, Integer candidateLimit) {
        EngineRequest request = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.LEADER,
                TargetType.STOCK,
                null,
                null,
                query.ruleVersion(),
                LEADER_REQUIRED_TABLES,
                Map.of()
        );
        return leaderCompetitionEngine.judgeCandidates(request, safeLeaderLimit(candidateLimit))
                .stream()
                .map(judgmentBlockAssembler::toBlock)
                .toList();
    }

    private JudgmentBlockVO<DivergenceConsensusDetail> buildDivergenceCard(MarketQuery query) {
        EngineRequest request = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.DIVERGENCE_CONSENSUS,
                TargetType.MARKET,
                "MARKET",
                "全市场",
                query.ruleVersion(),
                DIVERGENCE_REQUIRED_TABLES,
                Map.of()
        );
        return judgmentBlockAssembler.toBlock(divergenceConsensusEngine.judge(request));
    }

    private JudgmentBlockVO<RiskRadarDetail> buildRiskCard(MarketQuery query) {
        EngineRequest request = engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.RISK,
                TargetType.MARKET,
                "MARKET",
                "全市场",
                query.ruleVersion(),
                RISK_REQUIRED_TABLES,
                Map.of()
        );
        return judgmentBlockAssembler.toBlock(riskRadarEngine.judge(request));
    }

    private EngineRequest createMainlineRequest(MarketQuery query, String plateCode, String plateName, Map<String, Object> params) {
        return engineRequestFactory.create(
                query.tradeDate(),
                query.asOfDate(),
                query.judgementMode(),
                EngineType.MAINLINE,
                TargetType.PLATE,
                plateCode,
                plateName,
                query.ruleVersion(),
                MAINLINE_REQUIRED_TABLES,
                params
        );
    }

    private Map<String, Object> candidateParams(MainlineCandidate candidate) {
        return Map.of(
                "plateType", candidate.plateType(),
                "candidateRank", candidate.candidateRank(),
                "candidateScore", candidate.candidateScore(),
                "candidateReason", candidate.candidateReason()
        );
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private int safeLeaderLimit(Integer candidateLimit) {
        if (candidateLimit == null || candidateLimit <= 0) {
            return 3;
        }
        return Math.min(candidateLimit, 10);
    }
}
