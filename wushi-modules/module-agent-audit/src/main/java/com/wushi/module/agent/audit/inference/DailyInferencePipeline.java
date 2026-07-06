package com.wushi.module.agent.audit.inference;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.JudgementResult;
import com.wushi.module.emotion.engine.CycleRecognitionEngine;
import com.wushi.module.emotion.model.CycleJudgementDetail;
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
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.support.EngineRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DailyInferencePipeline {

    private static final List<String> CYCLE_TABLES = List.of("market_breadth_daily_snapshot", "stock_limit_status_daily");
    private static final List<String> MAINLINE_TABLES = List.of("plate_daily_snapshot", "capital_flow_daily_snapshot");
    private static final List<String> LEADER_TABLES = List.of("stock_limit_status_daily", "stock_daily_kline", "stock_limit_intraday_event", "plate_daily_snapshot", "stock_plate_relation_snapshot");
    private static final List<String> PATTERN_TABLES = List.of("stock_limit_status_daily", "stock_limit_intraday_event", "stock_daily_kline", "plate_daily_snapshot", "stock_plate_relation_snapshot", "high_position_feedback_daily");
    private static final List<String> RISK_TABLES = List.of("high_position_feedback_daily", "stock_limit_status_daily", "market_breadth_daily_snapshot", "plate_daily_snapshot");

    private final EngineRequestFactory engineRequestFactory;
    private final CycleRecognitionEngine cycleRecognitionEngine;
    private final MainlineRecognitionEngine mainlineRecognitionEngine;
    private final MainlineCandidateSelector mainlineCandidateSelector;
    private final LeaderCompetitionEngine leaderCompetitionEngine;
    private final DivergenceConsensusEngine divergenceConsensusEngine;
    private final RiskRadarEngine riskRadarEngine;

    public MarketInferenceContext infer(EngineRunContext runContext) {
        JudgementResult<CycleJudgementDetail> cycleResult = cycleRecognitionEngine.judge(request(
                runContext,
                EngineType.CYCLE,
                TargetType.MARKET,
                "MARKET",
                "全市场",
                CYCLE_TABLES,
                Map.of()
        ));
        MarketInferenceContext context = MarketInferenceContext.cycleOnly(
                runContext.getTradeDate(),
                runContext.getAsOfDate(),
                runContext.getJudgementMode().name(),
                runContext.getRuleVersion(),
                cycleResult
        );

        context = context.withMainlines(judgeMainlines(runContext, context));
        context = context.withLeaders(judgeLeaders(runContext, context));
        context = context.withDivergence(judgeDivergence(runContext, context));
        context = context.withRisk(judgeRisk(runContext, context));
        return context;
    }

    private List<JudgementResult<MainlineJudgementDetail>> judgeMainlines(
            EngineRunContext runContext,
            MarketInferenceContext context
    ) {
        List<MainlineCandidate> candidates = mainlineCandidateSelector.selectCandidates(runContext.getTradeDate(), 5);
        if (candidates == null || candidates.isEmpty()) {
            return List.of(mainlineRecognitionEngine.judge(request(
                    runContext,
                    EngineType.MAINLINE,
                    TargetType.PLATE,
                    null,
                    null,
                    MAINLINE_TABLES,
                    context.cycleParams()
            )));
        }

        List<JudgementResult<MainlineJudgementDetail>> results = new ArrayList<>();
        for (MainlineCandidate candidate : candidates) {
            Map<String, Object> params = new LinkedHashMap<>(context.cycleParams());
            params.put("candidate.rank", candidate.candidateRank());
            params.put("candidate.plateCode", candidate.plateCode());
            params.put("candidate.plateName", candidate.plateName());
            params.put("candidate.plateType", candidate.plateType());
            params.put("candidate.score", candidate.candidateScore());
            params.put("candidate.reason", candidate.candidateReason());
            results.add(mainlineRecognitionEngine.judge(request(
                    runContext,
                    EngineType.MAINLINE,
                    TargetType.PLATE,
                    candidate.plateCode(),
                    candidate.plateName(),
                    MAINLINE_TABLES,
                    params
            )));
        }
        return results;
    }

    private List<JudgementResult<LeaderJudgementDetail>> judgeLeaders(
            EngineRunContext runContext,
            MarketInferenceContext context
    ) {
        Map<String, Object> params = context.mainlineParams(context.topMainline().orElse(null));
        context.topMainline().ifPresent(mainline -> {
            params.put("scope.plateCode", mainline.getTargetCode());
            params.put("scope.plateName", mainline.getTargetName());
        });
        return leaderCompetitionEngine.judgeCandidates(request(
                runContext,
                EngineType.LEADER,
                TargetType.STOCK,
                null,
                null,
                LEADER_TABLES,
                params
        ), 5);
    }

    private JudgementResult<DivergenceConsensusDetail> judgeDivergence(
            EngineRunContext runContext,
            MarketInferenceContext context
    ) {
        JudgementResult<MainlineJudgementDetail> topMainline = context.topMainline().orElse(null);
        TargetType targetType = context.hasTopMainlineTarget() ? TargetType.PLATE : TargetType.MARKET;
        String targetCode = context.hasTopMainlineTarget() ? topMainline.getTargetCode() : "MARKET";
        String targetName = context.hasTopMainlineTarget() ? topMainline.getTargetName() : "全市场";
        return divergenceConsensusEngine.judge(request(
                runContext,
                EngineType.DIVERGENCE_CONSENSUS,
                targetType,
                targetCode,
                targetName,
                PATTERN_TABLES,
                context.leaderParams(context.topLeader().orElse(null))
        ));
    }

    private JudgementResult<RiskRadarDetail> judgeRisk(
            EngineRunContext runContext,
            MarketInferenceContext context
    ) {
        return riskRadarEngine.judge(request(
                runContext,
                EngineType.RISK,
                TargetType.MARKET,
                "MARKET",
                "全市场",
                RISK_TABLES,
                context.divergenceParams(context.divergenceResult())
        ));
    }

    private EngineRequest request(EngineRunContext context, EngineType engineType, TargetType targetType,
                                  String targetCode, String targetName, List<String> requiredTables,
                                  Map<String, Object> params) {
        return engineRequestFactory.create(
                context.getTradeDate(),
                context.getAsOfDate(),
                context.getJudgementMode(),
                engineType,
                targetType,
                targetCode,
                targetName,
                context.getRuleVersion(),
                requiredTables,
                params
        );
    }
}
