package com.wushi.module.pattern.engine.impl;

import com.wushi.common.enums.DivergenceConsensusState;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.EvidenceType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import com.wushi.module.pattern.engine.DivergenceConsensusEngine;
import com.wushi.module.pattern.factor.DivergenceConsensusFactorCalculator;
import com.wushi.module.pattern.model.DivergenceConsensusDetail;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.support.JudgementResultPostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultDivergenceConsensusEngine implements DivergenceConsensusEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final DivergenceConsensusFactorCalculator factorCalculator;
    private final JudgementResultPostProcessor resultPostProcessor;

    @Override
    public JudgementResult<DivergenceConsensusDetail> judge(EngineRequest request) {
        FactorCalculateResult factorResult = factorCalculator.calculate(toFactorRequest(request));
        PatternContext patternContext = PatternContext.from(request);
        PatternDecision decision = decide(factorResult.getFactorResults(), patternContext);
        TargetType targetType = defaultTargetType(request.targetType());
        String targetCode = defaultText(request.targetCode(), stringParam(request, "plateCode", "MARKET"));
        String targetName = defaultText(request.targetName(), stringParam(request, "plateName", "全市场"));
        String judgementId = "DIVERGENCE-" + request.tradeDate() + "-" + targetCode + "-" + UUID.randomUUID();

        JudgementResult<DivergenceConsensusDetail> result = JudgementResult.<DivergenceConsensusDetail>builder()
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .judgementMode(defaultMode(request.judgementMode()))
                .engineType(EngineType.DIVERGENCE_CONSENSUS)
                .targetType(targetType)
                .targetCode(targetCode)
                .targetName(targetName)
                .conclusion(enrichConclusion(decision.conclusion(), patternContext))
                .confidence(decision.confidence())
                .ruleVersion(request.ruleVersion())
                .dataQualityContext(request.dataQualityContext())
                .detail(new DivergenceConsensusDetail(
                        targetType,
                        targetCode,
                        targetName,
                        decision.state(),
                        value(factorResult.getFactorResults(), "PATTERN_DIVERGENCE_SCORE"),
                        value(factorResult.getFactorResults(), "PATTERN_CONSENSUS_SCORE"),
                        value(factorResult.getFactorResults(), "PATTERN_REFILL_QUALITY"),
                        value(factorResult.getFactorResults(), "PATTERN_BROKEN_LIMIT_RISK"),
                        value(factorResult.getFactorResults(), "PATTERN_REAR_FEEDBACK"),
                        value(factorResult.getFactorResults(), "PATTERN_TURNOVER_ACCEPTANCE"),
                        value(factorResult.getFactorResults(), "PATTERN_SHRINK_ACCELERATION"),
                        value(factorResult.getFactorResults(), "PATTERN_HIGH_POSITION_FEEDBACK"),
                        decision.confirmationSignal(),
                        decision.failureSignal(),
                        factorResult.getFactorResults(),
                        decision.satisfiedConditions(),
                        decision.unmetCondition(),
                        decision.tomorrowValidation(),
                        decision.patternReason(),
                        decision.patternRisk()
                ))
                .evidenceList(factorResult.getEvidenceList())
                .conflictList(factorResult.getConflictList())
                .warningList(factorResult.getWarningList())
                .nextWatchList(buildNextWatchList(judgementId, request, targetType, targetCode, targetName, decision))
                .build();

        return resultPostProcessor.apply(request, result);
    }

    private FactorCalculateRequest toFactorRequest(EngineRequest request) {
        return FactorCalculateRequest.builder()
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .engineType(EngineType.DIVERGENCE_CONSENSUS)
                .targetCode(request.targetCode())
                .targetName(request.targetName())
                .ruleContext(request.ruleContext())
                .facts(request.params())
                .params(request.params())
                .build();
    }

    private PatternDecision decide(List<FactorResult> factors, PatternContext context) {
        BigDecimal divergence = defaultZero(value(factors, "PATTERN_DIVERGENCE_SCORE"));
        BigDecimal consensus = defaultZero(value(factors, "PATTERN_CONSENSUS_SCORE"));
        BigDecimal refill = defaultZero(value(factors, "PATTERN_REFILL_QUALITY"));
        BigDecimal brokenRisk = defaultZero(value(factors, "PATTERN_BROKEN_LIMIT_RISK"));
        BigDecimal rear = defaultZero(value(factors, "PATTERN_REAR_FEEDBACK"));
        BigDecimal turnover = defaultZero(value(factors, "PATTERN_TURNOVER_ACCEPTANCE"));
        BigDecimal shrink = defaultZero(value(factors, "PATTERN_SHRINK_ACCELERATION"));
        BigDecimal highFeedback = defaultZero(value(factors, "PATTERN_HIGH_POSITION_FEEDBACK"));

        List<String> satisfied = new ArrayList<>();
        List<String> unmet = new ArrayList<>();
        collect(divergence.compareTo(new BigDecimal("0.6000")) >= 0, "分歧充分", "分歧不充分或事实不足", satisfied, unmet);
        collect(refill.compareTo(new BigDecimal("0.6000")) >= 0, "回封质量达标", "回封质量不足", satisfied, unmet);
        collect(consensus.compareTo(new BigDecimal("0.6500")) >= 0, "一致强度达标", "一致强度不足", satisfied, unmet);
        collect(rear.compareTo(new BigDecimal("0.5500")) >= 0, "后排承接修复", "后排承接不足", satisfied, unmet);
        collect(turnover.compareTo(new BigDecimal("0.5200")) >= 0, "换手承接健康", "换手承接不足或失控", satisfied, unmet);
        collect(shrink.compareTo(new BigDecimal("0.5500")) >= 0, "缩量加速成立", "缩量加速未确认", satisfied, unmet);
        collect(brokenRisk.compareTo(new BigDecimal("0.3500")) <= 0, "炸板风险可控", "炸板风险偏高", satisfied, unmet);
        collect(highFeedback.compareTo(new BigDecimal("0.4500")) <= 0, "高位负反馈可控", "高位负反馈偏强", satisfied, unmet);
        collect(context.hasConfirmedMainline(), "上游主线已确认或处于可验证阶段", "上游主线尚未确认，分歧转一致不能升级为可参与信号", satisfied, unmet);
        collect(context.hasLeaderSupport(), "龙头地位或分歧修复支持分歧判断", "龙头掉队、被挑战或分歧修复不足", satisfied, unmet);
        collect(!context.isCycleHardRisk(), "周期环境未处于硬风险边界", "周期处于退潮/亏钱扩散/少做边界", satisfied, unmet);

        DivergenceConsensusState rawState = decideState(divergence, consensus, refill, brokenRisk, rear, turnover, shrink, highFeedback);
        DivergenceConsensusState state = applyContext(rawState, context, brokenRisk, highFeedback, rear);
        BigDecimal confidence = adjustConfidence(
                confidence(score(factors), countEvidence(factors, EvidenceType.CONFLICT), countEvidence(factors, EvidenceType.WARNING), state),
                context,
                rawState,
                state
        );
        String reason = buildReason(state, divergence, consensus, refill, brokenRisk, rear, turnover, shrink, highFeedback, context);
        String risk = buildRisk(state, brokenRisk, highFeedback, rear, context);
        String confirmation = confirmationSignal(state, context);
        String failure = failureSignal(state, context);
        String tomorrow = tomorrowValidation(state, context);
        String conclusion = stateName(state) + "；" + reason + " 风险：" + risk;

        return new PatternDecision(state, confidence, conclusion, confirmation, failure,
                satisfied, String.join("、", unmet), tomorrow, reason, risk);
    }

    private DivergenceConsensusState applyContext(DivergenceConsensusState rawState, PatternContext context,
                                                  BigDecimal brokenRisk, BigDecimal highFeedback, BigDecimal rear) {
        if (context.isCycleHardRisk()
                && (rawState == DivergenceConsensusState.ACCELERATED_CONSENSUS
                || rawState == DivergenceConsensusState.OVERHEATED_CONSENSUS
                || brokenRisk.compareTo(new BigDecimal("0.3500")) > 0
                || highFeedback.compareTo(new BigDecimal("0.4500")) > 0)) {
            return DivergenceConsensusState.RECESSION_DIVERGENCE;
        }
        if (context.hasLeaderRisk()
                && (rawState == DivergenceConsensusState.DIVERGENCE_TO_CONSENSUS
                || rawState == DivergenceConsensusState.ACCELERATED_CONSENSUS
                || highFeedback.compareTo(new BigDecimal("0.4000")) > 0)) {
            return DivergenceConsensusState.RECESSION_DIVERGENCE;
        }
        if (!context.hasConfirmedMainline()) {
            if (rawState == DivergenceConsensusState.DIVERGENCE_TO_CONSENSUS
                    || rawState == DivergenceConsensusState.ACCELERATED_CONSENSUS) {
                return DivergenceConsensusState.WEAK_DIVERGENCE;
            }
            if (rawState == DivergenceConsensusState.STRONG_DIVERGENCE && rear.compareTo(new BigDecimal("0.5500")) < 0) {
                return DivergenceConsensusState.WEAK_DIVERGENCE;
            }
        }
        return rawState;
    }

    private BigDecimal adjustConfidence(BigDecimal confidence, PatternContext context,
                                        DivergenceConsensusState rawState, DivergenceConsensusState finalState) {
        BigDecimal adjusted = confidence;
        if (!context.hasConfirmedMainline()) {
            adjusted = adjusted.subtract(new BigDecimal("0.1000"));
        }
        if (!context.hasLeaderSupport()) {
            adjusted = adjusted.subtract(new BigDecimal("0.0800"));
        }
        if (context.isCycleHardRisk()) {
            adjusted = adjusted.subtract(new BigDecimal("0.0800"));
        }
        if (rawState != finalState) {
            adjusted = adjusted.subtract(new BigDecimal("0.0500"));
        }
        return adjusted.max(ZERO).min(ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private DivergenceConsensusState decideState(BigDecimal divergence, BigDecimal consensus, BigDecimal refill,
                                                 BigDecimal brokenRisk, BigDecimal rear, BigDecimal turnover,
                                                 BigDecimal shrink, BigDecimal highFeedback) {
        if (highFeedback.compareTo(new BigDecimal("0.6500")) >= 0
                || (divergence.compareTo(new BigDecimal("0.6500")) >= 0 && brokenRisk.compareTo(new BigDecimal("0.5500")) >= 0
                && rear.compareTo(new BigDecimal("0.4500")) < 0)) {
            return DivergenceConsensusState.RECESSION_DIVERGENCE;
        }
        if (consensus.compareTo(new BigDecimal("0.8200")) >= 0 && shrink.compareTo(new BigDecimal("0.7000")) >= 0
                && (brokenRisk.compareTo(new BigDecimal("0.3000")) > 0 || highFeedback.compareTo(new BigDecimal("0.3500")) > 0)) {
            return DivergenceConsensusState.OVERHEATED_CONSENSUS;
        }
        if (divergence.compareTo(new BigDecimal("0.6000")) >= 0 && refill.compareTo(new BigDecimal("0.6000")) >= 0
                && consensus.compareTo(new BigDecimal("0.6500")) >= 0 && rear.compareTo(new BigDecimal("0.5500")) >= 0
                && turnover.compareTo(new BigDecimal("0.5200")) >= 0 && brokenRisk.compareTo(new BigDecimal("0.3500")) <= 0
                && highFeedback.compareTo(new BigDecimal("0.4500")) <= 0) {
            return DivergenceConsensusState.DIVERGENCE_TO_CONSENSUS;
        }
        if (consensus.compareTo(new BigDecimal("0.7200")) >= 0 && shrink.compareTo(new BigDecimal("0.6200")) >= 0
                && brokenRisk.compareTo(new BigDecimal("0.3500")) <= 0) {
            return DivergenceConsensusState.ACCELERATED_CONSENSUS;
        }
        if (divergence.compareTo(new BigDecimal("0.6000")) >= 0 && brokenRisk.compareTo(new BigDecimal("0.4500")) < 0
                && highFeedback.compareTo(new BigDecimal("0.4500")) < 0) {
            return DivergenceConsensusState.STRONG_DIVERGENCE;
        }
        if (divergence.compareTo(new BigDecimal("0.3000")) > 0 || brokenRisk.compareTo(new BigDecimal("0.2500")) > 0) {
            return DivergenceConsensusState.WEAK_DIVERGENCE;
        }
        return DivergenceConsensusState.UNKNOWN;
    }

    private List<NextWatchItem> buildNextWatchList(String judgementId, EngineRequest request, TargetType targetType,
                                                   String targetCode, String targetName, PatternDecision decision) {
        LocalDate watchDate = request.tradeDate() == null ? null : request.tradeDate().plusDays(1);
        return List.of(
                watch(judgementId, request, watchDate, targetType, targetCode, targetName, "PATTERN_WATCH_REFILL",
                        "验证分歧回封是否延续", decision.confirmationSignal(),
                        "回封质量延续，支持分歧转一致或一致维持", decision.failureSignal(), 1),
                watch(judgementId, request, watchDate, targetType, targetCode, targetName, "PATTERN_WATCH_REAR",
                        "验证后排承接是否跟上", "后排炸板率下降，补涨不扩散亏钱，中位数表现不走弱",
                        "后排承接修复，说明一致有扩散基础", "后排继续炸板或亏钱扩散，分歧转退潮风险上升", 2),
                watch(judgementId, request, watchDate, targetType, targetCode, targetName, "PATTERN_WATCH_HIGH_POSITION",
                        "验证高位反馈是否失控", "高位股不出现跌停、大阴、断板后无修复",
                        "高位反馈可控，分歧仍属健康换手", "高位负反馈扩大，确认退潮分歧或一致过热兑现", 3)
        );
    }

    private NextWatchItem watch(String judgementId, EngineRequest request, LocalDate watchDate, TargetType targetType,
                                String targetCode, String targetName, String watchCode, String title, String condition,
                                String expectedSignal, String riskSignal, int priority) {
        return NextWatchItem.builder()
                .watchId(watchCode + "-" + request.tradeDate() + "-" + targetCode)
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .watchDate(watchDate)
                .engineType(EngineType.DIVERGENCE_CONSENSUS)
                .targetType(targetType)
                .targetCode(targetCode)
                .targetName(targetName)
                .watchCode(watchCode)
                .title(title)
                .conditionExpression(condition)
                .expectedSignal(expectedSignal)
                .riskSignal(riskSignal)
                .priority(priority)
                .ruleVersion(request.ruleVersion())
                .validationStatus(WatchValidationStatus.PENDING)
                .build();
    }

    private String buildReason(DivergenceConsensusState state, BigDecimal divergence, BigDecimal consensus,
                               BigDecimal refill, BigDecimal brokenRisk, BigDecimal rear, BigDecimal turnover,
                               BigDecimal shrink, BigDecimal highFeedback, PatternContext context) {
        return "分歧=" + divergence + "，一致=" + consensus + "，回封=" + refill
                + "，炸板风险=" + brokenRisk + "，后排=" + rear + "，换手承接=" + turnover
                + "，缩量加速=" + shrink + "，高位反馈=" + highFeedback
                + "；上游语境=" + context.describe()
                + "，综合判断为" + stateName(state);
    }

    private String buildRisk(DivergenceConsensusState state, BigDecimal brokenRisk, BigDecimal highFeedback, BigDecimal rear,
                             PatternContext context) {
        if (!context.hasConfirmedMainline()) {
            return "上游主线未确认，当前分歧只能当作题材试错或轮动波动，不能按主线分歧转一致参与。";
        }
        if (context.hasLeaderRisk()) {
            return "龙头掉队、被挑战或分歧修复不足，分歧更可能向退潮兑现。";
        }
        if (context.isCycleHardRisk()) {
            return "周期处于风险边界，分歧信号需要先验证亏钱效应是否收敛。";
        }
        if (state == DivergenceConsensusState.RECESSION_DIVERGENCE) {
            return "高位负反馈或炸板风险已经压过修复，优先按退潮分歧处理。";
        }
        if (state == DivergenceConsensusState.OVERHEATED_CONSENSUS) {
            return "一致过热后容易明牌兑现，重点防缩量加速后的放量分歧。";
        }
        if (brokenRisk.compareTo(new BigDecimal("0.3500")) > 0 || highFeedback.compareTo(new BigDecimal("0.4500")) > 0) {
            return "风险尚未完全收敛，分歧转一致需要明日继续验证。";
        }
        if (rear.compareTo(new BigDecimal("0.5500")) < 0) {
            return "后排承接不足，核心修复可能只是局部抱团。";
        }
        return "风险暂可控，关键在于明日后排和高位反馈能否继续确认。";
    }

    private String confirmationSignal(DivergenceConsensusState state, PatternContext context) {
        String base = switch (state) {
            case DIVERGENCE_TO_CONSENSUS -> "明日核心股继续回封或弱转强，后排炸板下降，板块中位数不走弱。";
            case ACCELERATED_CONSENSUS -> "明日缩量加速后仍有承接，后排不掉队，高位不出现负反馈。";
            case STRONG_DIVERGENCE, WEAK_DIVERGENCE -> "明日分歧后出现回封、换手承接和后排修复。";
            case OVERHEATED_CONSENSUS -> "明日若放量仍不杀高位，说明过热暂未兑现。";
            case RECESSION_DIVERGENCE -> "明日必须出现核心修复和亏钱效应收敛，否则确认退潮。";
            case UNKNOWN -> "补齐炸板、回封、后排和高位反馈事实后再确认。";
        };
        return base + " 同时验证：" + context.validationFocus();
    }

    private String failureSignal(DivergenceConsensusState state, PatternContext context) {
        String base = switch (state) {
            case DIVERGENCE_TO_CONSENSUS, ACCELERATED_CONSENSUS -> "核心回封失败、后排炸板扩散或高位负反馈增强。";
            case STRONG_DIVERGENCE, WEAK_DIVERGENCE -> "分歧后无回封，炸板继续扩大，后排承接消失。";
            case OVERHEATED_CONSENSUS -> "缩量加速后放量杀跌，明牌兑现，高位大阴或跌停。";
            case RECESSION_DIVERGENCE -> "高位继续负反馈，跌停/炸板扩大，修复失败。";
            case UNKNOWN -> "关键事实仍缺失，判断保持低置信。";
        };
        return base + " 若上游主线或龙头同步走弱，则按退潮确认。";
    }

    private String tomorrowValidation(DivergenceConsensusState state, PatternContext context) {
        return confirmationSignal(state, context) + " 反向观察：" + failureSignal(state, context);
    }

    private String stateName(DivergenceConsensusState state) {
        return switch (state) {
            case WEAK_DIVERGENCE -> "弱分歧";
            case STRONG_DIVERGENCE -> "健康强分歧";
            case DIVERGENCE_TO_CONSENSUS -> "分歧转一致";
            case ACCELERATED_CONSENSUS -> "加速一致";
            case OVERHEATED_CONSENSUS -> "一致过热";
            case RECESSION_DIVERGENCE -> "退潮分歧";
            case UNKNOWN -> "分歧一致状态不明";
        };
    }

    private void collect(boolean passed, String satisfiedText, String unmetText, List<String> satisfied, List<String> unmet) {
        if (passed) {
            satisfied.add(satisfiedText);
        } else {
            unmet.add(unmetText);
        }
    }

    private BigDecimal value(List<FactorResult> factors, String factorCode) {
        if (factors == null) {
            return null;
        }
        return factors.stream()
                .filter(factor -> factorCode.equals(factor.getFactorCode()))
                .findFirst()
                .map(FactorResult::getFactorValue)
                .orElse(null);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private int countEvidence(List<FactorResult> factors, EvidenceType evidenceType) {
        if (factors == null) {
            return 0;
        }
        return (int) factors.stream()
                .filter(factor -> factor.getEvidenceType() == evidenceType)
                .count();
    }

    private BigDecimal score(List<FactorResult> factors) {
        BigDecimal totalWeight = ZERO;
        BigDecimal weightedScore = ZERO;
        if (factors == null) {
            return ZERO;
        }
        for (FactorResult factor : factors) {
            BigDecimal weight = factor.getWeight() == null ? ZERO : factor.getWeight();
            BigDecimal score = factor.getScore() == null ? ZERO : factor.getScore();
            totalWeight = totalWeight.add(weight);
            weightedScore = weightedScore.add(score.multiply(weight));
        }
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return weightedScore.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal confidence(BigDecimal score, int conflictCount, int warningCount, DivergenceConsensusState state) {
        BigDecimal penalty = BigDecimal.valueOf(conflictCount).multiply(new BigDecimal("0.0500"))
                .add(BigDecimal.valueOf(warningCount).multiply(new BigDecimal("0.0300")));
        if (state == DivergenceConsensusState.UNKNOWN) {
            penalty = penalty.add(new BigDecimal("0.1500"));
        }
        BigDecimal confidence = score.multiply(new BigDecimal("0.7000"))
                .add(new BigDecimal("0.3000"))
                .subtract(penalty);
        if (confidence.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (confidence.compareTo(ONE) > 0) {
            return ONE;
        }
        return confidence.setScale(4, RoundingMode.HALF_UP);
    }

    private TargetType defaultTargetType(TargetType targetType) {
        return targetType == null ? TargetType.MARKET : targetType;
    }

    private JudgementMode defaultMode(JudgementMode judgementMode) {
        return judgementMode == null ? JudgementMode.REALTIME : judgementMode;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String stringParam(EngineRequest request, String key, String defaultValue) {
        if (request.params() == null || request.params().get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(request.params().get(key));
    }

    private String enrichConclusion(String conclusion, PatternContext context) {
        if (!context.hasAny()) {
            return conclusion;
        }
        return conclusion + "（已纳入上游链路：" + context.describe() + "）";
    }

    private static String stringParamValue(EngineRequest request, String key) {
        if (request.params() == null || request.params().get(key) == null) {
            return null;
        }
        return String.valueOf(request.params().get(key));
    }

    private static BigDecimal decimalParamValue(EngineRequest request, String key) {
        if (request.params() == null || request.params().get(key) == null) {
            return null;
        }
        Object value = request.params().get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(4, RoundingMode.HALF_UP);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text).setScale(4, RoundingMode.HALF_UP);
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static boolean containsAny(String text, String... keywords) {
        if (!hasText(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String blankToDefault(String text, String defaultText) {
        return hasText(text) ? text : defaultText;
    }

    private record PatternContext(
            String cycleStage,
            String emotionStage,
            String strategyBoundary,
            String mainlineStatus,
            String mainlineLifecycle,
            String mainlineConclusion,
            BigDecimal mainlineConfidence,
            String leaderType,
            String leaderStatus,
            BigDecimal leaderRepairScore,
            BigDecimal leaderDriveScore,
            String leaderConclusion,
            BigDecimal leaderConfidence
    ) {
        private static PatternContext from(EngineRequest request) {
            return new PatternContext(
                    stringParamValue(request, "cycle.marketCycleStage"),
                    stringParamValue(request, "cycle.emotionCycleStage"),
                    stringParamValue(request, "cycle.strategyBoundary"),
                    stringParamValue(request, "mainline.status"),
                    blankToDefault(stringParamValue(request, "mainline.lifecycleStageName"), stringParamValue(request, "mainline.lifecycleStage")),
                    stringParamValue(request, "upstreamMainline.conclusion"),
                    decimalParamValue(request, "upstreamMainline.confidence"),
                    stringParamValue(request, "leader.leaderType"),
                    stringParamValue(request, "leader.leaderStatus"),
                    decimalParamValue(request, "leader.divergenceRepairScore"),
                    decimalParamValue(request, "leader.driveScore"),
                    stringParamValue(request, "upstreamLeader.conclusion"),
                    decimalParamValue(request, "upstreamLeader.confidence")
            );
        }

        private boolean hasAny() {
            return hasText(cycleStage) || hasText(emotionStage) || hasText(strategyBoundary)
                    || hasText(mainlineStatus) || hasText(mainlineLifecycle) || hasText(mainlineConclusion)
                    || hasText(leaderType) || hasText(leaderStatus) || hasText(leaderConclusion);
        }

        private boolean hasConfirmedMainline() {
            String status = blankToDefault(mainlineStatus, "");
            String text = describeMainline();
            boolean statusConfirmed = "MAIN".equals(status);
            boolean lifecycleConfirmed = containsAny(blankToDefault(mainlineLifecycle, ""),
                    "CONFIRMATION", "ACCELERATION", "DIVERGENCE", "CONSENSUS", "确认", "加速", "分歧", "一致");
            boolean conclusionConfirmed = containsAny(blankToDefault(mainlineConclusion, ""), "主线确认", "可参与");
            return statusConfirmed || lifecycleConfirmed || conclusionConfirmed;
        }

        private boolean hasLeaderSupport() {
            if (!hasLeaderContext()) {
                return true;
            }
            String text = describeLeader();
            boolean statusSupport = containsAny(text, "MARKET", "MAINLINE", "BRANCH", "STABLE", "RISING", "CANDIDATE",
                    "总龙", "主线龙", "分支龙", "稳定", "上位", "候选");
            boolean repairSupport = leaderRepairScore != null && leaderRepairScore.compareTo(new BigDecimal("0.5000")) >= 0;
            boolean driveSupport = leaderDriveScore != null && leaderDriveScore.compareTo(new BigDecimal("0.4500")) >= 0;
            return statusSupport || repairSupport || driveSupport;
        }

        private boolean hasLeaderRisk() {
            if (!hasLeaderContext()) {
                return false;
            }
            String text = describeLeader();
            boolean statusRisk = containsAny(text, "DEAD", "DROPPED", "CHALLENGED", "掉队", "死亡", "被挑战");
            boolean repairRisk = leaderRepairScore != null && leaderRepairScore.compareTo(new BigDecimal("0.3500")) < 0;
            return statusRisk || repairRisk;
        }

        private boolean hasLeaderContext() {
            return hasText(leaderType) || hasText(leaderStatus) || hasText(leaderConclusion)
                    || leaderRepairScore != null || leaderDriveScore != null || leaderConfidence != null;
        }

        private boolean isCycleHardRisk() {
            String text = describeCycle();
            return containsAny(text, "熊头", "熊中", "退潮", "杀跌", "亏钱效应扩散", "少做", "不参与");
        }

        private String describe() {
            return "周期[" + describeCycle() + "]，主线[" + describeMainline() + "]，龙头[" + describeLeader() + "]";
        }

        private String describeCycle() {
            return String.join("/", blankToDefault(cycleStage, ""), blankToDefault(emotionStage, ""), blankToDefault(strategyBoundary, ""));
        }

        private String describeMainline() {
            return String.join("/", blankToDefault(mainlineStatus, ""), blankToDefault(mainlineLifecycle, ""),
                    blankToDefault(mainlineConclusion, ""), mainlineConfidence == null ? "" : "confidence=" + mainlineConfidence);
        }

        private String describeLeader() {
            return String.join("/", blankToDefault(leaderType, ""), blankToDefault(leaderStatus, ""),
                    blankToDefault(leaderConclusion, ""), leaderRepairScore == null ? "" : "repair=" + leaderRepairScore,
                    leaderDriveScore == null ? "" : "drive=" + leaderDriveScore,
                    leaderConfidence == null ? "" : "confidence=" + leaderConfidence);
        }

        private String validationFocus() {
            List<String> focuses = new ArrayList<>();
            if (!hasConfirmedMainline()) {
                focuses.add("主线是否补齐确认条件");
            }
            if (!hasLeaderSupport()) {
                focuses.add("龙头是否弱转强或分歧回封");
            }
            if (isCycleHardRisk()) {
                focuses.add("周期亏钱效应是否收敛");
            }
            if (focuses.isEmpty()) {
                focuses.add("主线、龙头、后排是否同步修复");
            }
            return String.join("；", focuses);
        }
    }

    private record PatternDecision(
            DivergenceConsensusState state,
            BigDecimal confidence,
            String conclusion,
            String confirmationSignal,
            String failureSignal,
            List<String> satisfiedConditions,
            String unmetCondition,
            String tomorrowValidation,
            String patternReason,
            String patternRisk
    ) {
    }
}
