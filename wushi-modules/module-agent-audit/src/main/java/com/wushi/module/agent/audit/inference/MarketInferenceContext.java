package com.wushi.module.agent.audit.inference;

import com.wushi.common.model.JudgementResult;
import com.wushi.module.emotion.model.CycleJudgementDetail;
import com.wushi.module.leader.model.LeaderJudgementDetail;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
import com.wushi.module.risk.model.RiskRadarDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record MarketInferenceContext(
        LocalDate tradeDate,
        LocalDate asOfDate,
        String judgementMode,
        String ruleVersion,
        JudgementResult<CycleJudgementDetail> cycleResult,
        List<JudgementResult<MainlineJudgementDetail>> mainlineResults,
        List<JudgementResult<LeaderJudgementDetail>> leaderResults,
        JudgementResult<DivergenceConsensusDetail> divergenceResult,
        JudgementResult<RiskRadarDetail> riskResult
) {

    public MarketInferenceContext {
        mainlineResults = mainlineResults == null ? List.of() : List.copyOf(mainlineResults);
        leaderResults = leaderResults == null ? List.of() : List.copyOf(leaderResults);
    }

    public Optional<JudgementResult<MainlineJudgementDetail>> topMainline() {
        return mainlineResults.stream().findFirst();
    }

    public Optional<JudgementResult<LeaderJudgementDetail>> topLeader() {
        return leaderResults.stream().findFirst();
    }

    public List<JudgementResult<?>> allResults() {
        List<JudgementResult<?>> results = new ArrayList<>();
        addIfPresent(results, cycleResult);
        results.addAll(mainlineResults);
        results.addAll(leaderResults);
        addIfPresent(results, divergenceResult);
        addIfPresent(results, riskResult);
        return results;
    }

    public Map<String, Object> cycleParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        putResult(params, "upstreamCycle", cycleResult);
        if (cycleResult != null && cycleResult.getDetail() != null) {
            CycleJudgementDetail detail = cycleResult.getDetail();
            put(params, "cycle.marketCycleStage", detail.marketCycleStage());
            put(params, "cycle.emotionCycleStage", detail.emotionCycleStage());
            put(params, "cycle.strategyBoundary", detail.strategyBoundary());
            put(params, "cycle.allowedMode", detail.allowedMode());
            put(params, "cycle.falseSignalRisk", detail.falseSignalRisk());
            put(params, "cycle.moneyEffectScore", detail.moneyEffectScore());
            put(params, "cycle.lossEffectScore", detail.lossEffectScore());
        }
        return params;
    }

    public Map<String, Object> mainlineParams(JudgementResult<MainlineJudgementDetail> mainlineResult) {
        Map<String, Object> params = cycleParams();
        putResult(params, "upstreamMainline", mainlineResult);
        if (mainlineResult != null && mainlineResult.getDetail() != null) {
            MainlineJudgementDetail detail = mainlineResult.getDetail();
            put(params, "mainline.plateCode", detail.plateCode());
            put(params, "mainline.plateName", detail.plateName());
            put(params, "mainline.status", detail.mainlineStatus());
            put(params, "mainline.lifecycleStage", detail.lifecycleStage());
            put(params, "mainline.lifecycleStageName", detail.lifecycleStageName());
            put(params, "mainline.participationDecision", detail.participationDecision());
            put(params, "mainline.strengthScore", detail.strengthScore());
            put(params, "mainline.continuityScore", detail.continuityScore());
            put(params, "mainline.rearRiskScore", detail.rearRiskScore());
        }
        return params;
    }

    public Map<String, Object> leaderParams(JudgementResult<LeaderJudgementDetail> leaderResult) {
        Map<String, Object> params = mainlineParams(topMainline().orElse(null));
        putResult(params, "upstreamLeader", leaderResult);
        if (leaderResult != null && leaderResult.getDetail() != null) {
            LeaderJudgementDetail detail = leaderResult.getDetail();
            put(params, "leader.stockCode", detail.stockCode());
            put(params, "leader.stockName", detail.stockName());
            put(params, "leader.leaderType", detail.leaderType());
            put(params, "leader.leaderStatus", detail.leaderStatus());
            put(params, "leader.positionScore", detail.positionScore());
            put(params, "leader.driveScore", detail.driveScore());
            put(params, "leader.divergenceRepairScore", detail.divergenceRepairScore());
            put(params, "leader.challengeRiskScore", detail.challengeRiskScore());
        }
        return params;
    }

    public Map<String, Object> divergenceParams(JudgementResult<DivergenceConsensusDetail> divergence) {
        Map<String, Object> params = leaderParams(topLeader().orElse(null));
        putResult(params, "upstreamDivergence", divergence);
        if (divergence != null && divergence.getDetail() != null) {
            DivergenceConsensusDetail detail = divergence.getDetail();
            put(params, "divergence.state", detail.state());
            put(params, "divergence.divergenceScore", detail.divergenceScore());
            put(params, "divergence.consensusScore", detail.consensusScore());
            put(params, "divergence.refillQualityScore", detail.refillQualityScore());
            put(params, "divergence.brokenLimitRiskScore", detail.brokenLimitRiskScore());
            put(params, "divergence.highPositionFeedbackScore", detail.highPositionFeedbackScore());
        }
        return params;
    }

    private static void putResult(Map<String, Object> params, String prefix, JudgementResult<?> result) {
        if (result == null) {
            return;
        }
        put(params, prefix + ".judgementId", result.getJudgementId());
        put(params, prefix + ".engineType", result.getEngineType());
        put(params, prefix + ".targetType", result.getTargetType());
        put(params, prefix + ".targetCode", result.getTargetCode());
        put(params, prefix + ".targetName", result.getTargetName());
        put(params, prefix + ".conclusion", result.getConclusion());
        put(params, prefix + ".confidence", result.getConfidence());
        put(params, prefix + ".ruleVersion", result.getRuleVersion());
        put(params, prefix + ".evidenceCount", count(result.getEvidenceList()));
        put(params, prefix + ".conflictCount", count(result.getConflictList()));
        put(params, prefix + ".warningCount", count(result.getWarningList()));
        put(params, prefix + ".nextWatchCount", count(result.getNextWatchList()));
    }

    private static int count(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private static void put(Map<String, Object> params, String key, Object value) {
        if (value instanceof BigDecimal decimal) {
            params.put(key, decimal);
            return;
        }
        if (value != null) {
            params.put(key, value.toString());
        }
    }

    private static void addIfPresent(List<JudgementResult<?>> results, JudgementResult<?> result) {
        if (result != null) {
            results.add(result);
        }
    }

    public static MarketInferenceContext cycleOnly(
            LocalDate tradeDate,
            LocalDate asOfDate,
            String judgementMode,
            String ruleVersion,
            JudgementResult<CycleJudgementDetail> cycleResult
    ) {
        return new MarketInferenceContext(
                tradeDate,
                asOfDate,
                judgementMode,
                ruleVersion,
                cycleResult,
                List.of(),
                List.of(),
                null,
                null
        );
    }

    public MarketInferenceContext withMainlines(List<JudgementResult<MainlineJudgementDetail>> mainlines) {
        return new MarketInferenceContext(tradeDate, asOfDate, judgementMode, ruleVersion,
                cycleResult, mainlines, leaderResults, divergenceResult, riskResult);
    }

    public MarketInferenceContext withLeaders(List<JudgementResult<LeaderJudgementDetail>> leaders) {
        return new MarketInferenceContext(tradeDate, asOfDate, judgementMode, ruleVersion,
                cycleResult, mainlineResults, leaders, divergenceResult, riskResult);
    }

    public MarketInferenceContext withDivergence(JudgementResult<DivergenceConsensusDetail> divergence) {
        return new MarketInferenceContext(tradeDate, asOfDate, judgementMode, ruleVersion,
                cycleResult, mainlineResults, leaderResults, divergence, riskResult);
    }

    public MarketInferenceContext withRisk(JudgementResult<RiskRadarDetail> risk) {
        return new MarketInferenceContext(tradeDate, asOfDate, judgementMode, ruleVersion,
                cycleResult, mainlineResults, leaderResults, divergenceResult, risk);
    }

    public boolean hasTopMainlineTarget() {
        return topMainline()
                .map(JudgementResult::getTargetCode)
                .map(code -> !code.isBlank())
                .orElse(false);
    }
}
