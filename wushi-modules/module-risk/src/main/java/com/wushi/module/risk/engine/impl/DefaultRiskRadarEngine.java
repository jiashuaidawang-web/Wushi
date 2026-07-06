package com.wushi.module.risk.engine.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.EvidenceType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.RiskLevel;
import com.wushi.common.enums.RiskType;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import com.wushi.module.risk.engine.RiskRadarEngine;
import com.wushi.module.risk.factor.RiskFactorCalculator;
import com.wushi.module.risk.model.RiskRadarDetail;
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
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultRiskRadarEngine implements RiskRadarEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final RiskFactorCalculator riskFactorCalculator;
    private final JudgementResultPostProcessor resultPostProcessor;

    @Override
    public JudgementResult<RiskRadarDetail> judge(EngineRequest request) {
        FactorCalculateResult factorResult = riskFactorCalculator.calculate(toFactorRequest(request));
        RiskChainContext chainContext = RiskChainContext.from(request);
        RiskDecision decision = decide(factorResult.getFactorResults(), chainContext);
        TargetType targetType = request.targetType() == null ? TargetType.MARKET : request.targetType();
        String targetCode = defaultText(request.targetCode(), "MARKET");
        String targetName = defaultText(request.targetName(), "全市场");
        String judgementId = "RISK-" + request.tradeDate() + "-" + targetCode + "-" + UUID.randomUUID();

        JudgementResult<RiskRadarDetail> result = JudgementResult.<RiskRadarDetail>builder()
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .judgementMode(request.judgementMode() == null ? JudgementMode.REALTIME : request.judgementMode())
                .engineType(EngineType.RISK)
                .targetType(targetType)
                .targetCode(targetCode)
                .targetName(targetName)
                .conclusion(enrichConclusion(decision.conclusion(), chainContext))
                .confidence(decision.confidence())
                .ruleVersion(request.ruleVersion())
                .dataQualityContext(request.dataQualityContext())
                .detail(new RiskRadarDetail(
                        targetType,
                        targetCode,
                        targetName,
                        decision.riskLevel(),
                        decision.riskType(),
                        decision.riskScore(),
                        value(factorResult.getFactorResults(), "RISK_HIGH_POSITION_FEEDBACK"),
                        value(factorResult.getFactorResults(), "RISK_BROKEN_LIMIT_RATE"),
                        value(factorResult.getFactorResults(), "RISK_LOSS_SPREAD"),
                        decision.reason(),
                        decision.reduceSignal()
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
                .engineType(EngineType.RISK)
                .targetCode(request.targetCode())
                .targetName(request.targetName())
                .ruleContext(request.ruleContext())
                .facts(request.params())
                .params(request.params())
                .build();
    }

    private RiskDecision decide(List<FactorResult> factors, RiskChainContext context) {
        BigDecimal high = defaultZero(value(factors, "RISK_HIGH_POSITION_FEEDBACK"));
        BigDecimal broken = defaultZero(value(factors, "RISK_BROKEN_LIMIT_RATE"));
        BigDecimal loss = defaultZero(value(factors, "RISK_LOSS_SPREAD"));
        BigDecimal leader = defaultZero(value(factors, "RISK_LEADER_FAIL"));
        BigDecimal plate = defaultZero(value(factors, "RISK_PLATE_LOSS"));
        BigDecimal factorRiskScore = high.multiply(new BigDecimal("0.2600"))
                .add(broken.multiply(new BigDecimal("0.2200")))
                .add(loss.multiply(new BigDecimal("0.2000")))
                .add(leader.multiply(new BigDecimal("0.1800")))
                .add(plate.multiply(new BigDecimal("0.1400")))
                .min(ONE).setScale(4, RoundingMode.HALF_UP);
        BigDecimal chainRiskScore = context.chainRiskScore();
        BigDecimal riskScore = factorRiskScore.multiply(new BigDecimal("0.7000"))
                .add(chainRiskScore.multiply(new BigDecimal("0.3000")))
                .max(factorRiskScore)
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
        RiskLevel level = riskScore.compareTo(new BigDecimal("0.7500")) >= 0 ? RiskLevel.EXTREME
                : riskScore.compareTo(new BigDecimal("0.5500")) >= 0 ? RiskLevel.HIGH
                : riskScore.compareTo(new BigDecimal("0.3200")) >= 0 ? RiskLevel.MEDIUM
                : RiskLevel.LOW;
        RiskType type = maxType(high, broken, loss, leader, plate, context);
        BigDecimal confidence = adjustConfidence(
                confidence(score(factors), countEvidence(factors, EvidenceType.CONFLICT), countEvidence(factors, EvidenceType.WARNING)),
                context
        );
        String reason = "风险分=" + riskScore + "，高位反馈=" + high + "，炸板率=" + broken
                + "，亏钱扩散=" + loss + "，龙头失败=" + leader + "，板块失速=" + plate
                + "，链路风险=" + chainRiskScore + "；上游链路=" + context.describe();
        String reduce = reduceSignal(context);
        String conclusion = level + "/" + type + "；" + reason;
        return new RiskDecision(level, type, riskScore, confidence, reason, reduce, conclusion);
    }

    private RiskType maxType(BigDecimal high, BigDecimal broken, BigDecimal loss, BigDecimal leader, BigDecimal plate,
                             RiskChainContext context) {
        if (context.hasLeaderRisk()) {
            return RiskType.LEADER_FAIL;
        }
        if (context.isCycleHardRisk() || context.isDivergenceRecession()) {
            return RiskType.RECESSION;
        }
        if (context.hasMainlineRisk()) {
            return RiskType.PLATE_LOSS;
        }
        BigDecimal max = high;
        RiskType type = RiskType.HIGH_POSITION_FEEDBACK;
        if (broken.compareTo(max) > 0) {
            max = broken;
            type = RiskType.REAR_BROKEN_LIMIT;
        }
        if (loss.compareTo(max) > 0) {
            max = loss;
            type = RiskType.RECESSION;
        }
        if (leader.compareTo(max) > 0) {
            max = leader;
            type = RiskType.LEADER_FAIL;
        }
        if (plate.compareTo(max) > 0) {
            type = RiskType.PLATE_LOSS;
        }
        return type;
    }

    private BigDecimal adjustConfidence(BigDecimal confidence, RiskChainContext context) {
        BigDecimal adjusted = confidence;
        if (!context.hasAny()) {
            return adjusted.subtract(new BigDecimal("0.0600")).max(ZERO).setScale(4, RoundingMode.HALF_UP);
        }
        if (context.hasContradictoryOpportunity()) {
            adjusted = adjusted.subtract(new BigDecimal("0.0500"));
        }
        if (context.isCycleHardRisk() || context.hasLeaderRisk() || context.isDivergenceRecession()) {
            adjusted = adjusted.add(new BigDecimal("0.0400"));
        }
        return adjusted.max(ZERO).min(ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private String reduceSignal(RiskChainContext context) {
        StringBuilder builder = new StringBuilder("明日验证高位负反馈是否收敛、炸板率是否下降、亏钱效应是否停止扩散。");
        if (context.isCycleHardRisk()) {
            builder.append(" 周期层面重点看亏钱效应是否衰竭、市场宽度是否修复。");
        }
        if (context.hasMainlineRisk()) {
            builder.append(" 主线层面重点看核心板块是否停止失速、后排是否不再扩散大面。");
        }
        if (context.hasLeaderRisk()) {
            builder.append(" 龙头层面重点看是否弱转强、分歧回封，若继续掉队则确认风险兑现。");
        }
        if (context.hasDivergenceRisk()) {
            builder.append(" 分歧一致层面重点看退潮分歧是否修复，若一致过热后放量杀跌则按收割处理。");
        }
        return builder.toString();
    }

    private List<NextWatchItem> buildNextWatchList(String judgementId, EngineRequest request, TargetType targetType,
                                                   String targetCode, String targetName, RiskDecision decision) {
        LocalDate watchDate = request.tradeDate() == null ? null : request.tradeDate().plusDays(1);
        return List.of(watch(judgementId, request, watchDate, targetType, targetCode, targetName, "RISK_WATCH_REDUCE",
                "验证风险是否收敛", decision.reduceSignal(),
                "风险收敛，机会可重新从主线和龙头修复里找", "风险继续扩散，降低仓位并等待新周期", 1));
    }

    private NextWatchItem watch(String judgementId, EngineRequest request, LocalDate watchDate, TargetType targetType,
                                String targetCode, String targetName, String watchCode, String title,
                                String condition, String expectedSignal, String riskSignal, int priority) {
        return NextWatchItem.builder()
                .watchId(watchCode + "-" + request.tradeDate() + "-" + targetCode)
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .watchDate(watchDate)
                .engineType(EngineType.RISK)
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

    private BigDecimal value(List<FactorResult> factors, String factorCode) {
        return factors.stream().filter(factor -> factorCode.equals(factor.getFactorCode()))
                .findFirst().map(FactorResult::getFactorValue).orElse(null);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal score(List<FactorResult> factors) {
        BigDecimal totalWeight = ZERO;
        BigDecimal weightedScore = ZERO;
        for (FactorResult factor : factors) {
            BigDecimal weight = factor.getWeight() == null ? ZERO : factor.getWeight();
            BigDecimal score = factor.getScore() == null ? ZERO : factor.getScore();
            totalWeight = totalWeight.add(weight);
            weightedScore = weightedScore.add(score.multiply(weight));
        }
        return totalWeight.compareTo(BigDecimal.ZERO) <= 0 ? ZERO : weightedScore.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private int countEvidence(List<FactorResult> factors, EvidenceType evidenceType) {
        return (int) factors.stream().filter(factor -> factor.getEvidenceType() == evidenceType).count();
    }

    private BigDecimal confidence(BigDecimal score, int conflictCount, int warningCount) {
        BigDecimal confidence = score.multiply(new BigDecimal("0.7000")).add(new BigDecimal("0.3000"))
                .subtract(BigDecimal.valueOf(conflictCount).multiply(new BigDecimal("0.0500")))
                .subtract(BigDecimal.valueOf(warningCount).multiply(new BigDecimal("0.0300")));
        return confidence.max(ZERO).min(ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String enrichConclusion(String conclusion, RiskChainContext context) {
        if (!context.hasAny()) {
            return conclusion;
        }
        return conclusion + "（已纳入完整链路：" + context.describe() + "）";
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

    private static String blankToDefault(String text, String defaultValue) {
        return hasText(text) ? text : defaultValue;
    }

    private record RiskChainContext(
            String cycleStage,
            String emotionStage,
            String strategyBoundary,
            String cycleConclusion,
            BigDecimal cycleConfidence,
            String mainlineStatus,
            String mainlineLifecycle,
            String mainlineParticipation,
            BigDecimal mainlineRearRisk,
            String mainlineConclusion,
            BigDecimal mainlineConfidence,
            String leaderType,
            String leaderStatus,
            BigDecimal leaderRepairScore,
            BigDecimal leaderDriveScore,
            BigDecimal leaderChallengeRiskScore,
            String leaderConclusion,
            BigDecimal leaderConfidence,
            String divergenceState,
            BigDecimal divergenceScore,
            BigDecimal consensusScore,
            BigDecimal brokenLimitRiskScore,
            BigDecimal highPositionFeedbackScore,
            String divergenceConclusion,
            BigDecimal divergenceConfidence
    ) {
        private static RiskChainContext from(EngineRequest request) {
            return new RiskChainContext(
                    stringParamValue(request, "cycle.marketCycleStage"),
                    stringParamValue(request, "cycle.emotionCycleStage"),
                    stringParamValue(request, "cycle.strategyBoundary"),
                    stringParamValue(request, "upstreamCycle.conclusion"),
                    decimalParamValue(request, "upstreamCycle.confidence"),
                    stringParamValue(request, "mainline.status"),
                    blankToDefault(stringParamValue(request, "mainline.lifecycleStageName"), stringParamValue(request, "mainline.lifecycleStage")),
                    stringParamValue(request, "mainline.participationDecision"),
                    decimalParamValue(request, "mainline.rearRiskScore"),
                    stringParamValue(request, "upstreamMainline.conclusion"),
                    decimalParamValue(request, "upstreamMainline.confidence"),
                    stringParamValue(request, "leader.leaderType"),
                    stringParamValue(request, "leader.leaderStatus"),
                    decimalParamValue(request, "leader.divergenceRepairScore"),
                    decimalParamValue(request, "leader.driveScore"),
                    decimalParamValue(request, "leader.challengeRiskScore"),
                    stringParamValue(request, "upstreamLeader.conclusion"),
                    decimalParamValue(request, "upstreamLeader.confidence"),
                    stringParamValue(request, "divergence.state"),
                    decimalParamValue(request, "divergence.divergenceScore"),
                    decimalParamValue(request, "divergence.consensusScore"),
                    decimalParamValue(request, "divergence.brokenLimitRiskScore"),
                    decimalParamValue(request, "divergence.highPositionFeedbackScore"),
                    stringParamValue(request, "upstreamDivergence.conclusion"),
                    decimalParamValue(request, "upstreamDivergence.confidence")
            );
        }

        private boolean hasAny() {
            return hasText(cycleStage) || hasText(emotionStage) || hasText(strategyBoundary) || hasText(cycleConclusion)
                    || hasText(mainlineStatus) || hasText(mainlineLifecycle) || hasText(mainlineConclusion)
                    || hasText(leaderType) || hasText(leaderStatus) || hasText(leaderConclusion)
                    || hasText(divergenceState) || hasText(divergenceConclusion);
        }

        private BigDecimal chainRiskScore() {
            BigDecimal score = ZERO;
            if (isCycleHardRisk()) {
                score = score.add(new BigDecimal("0.2800"));
            }
            if (hasMainlineRisk()) {
                score = score.add(new BigDecimal("0.2200"));
            }
            if (hasLeaderRisk()) {
                score = score.add(new BigDecimal("0.2200"));
            }
            if (hasDivergenceRisk()) {
                score = score.add(new BigDecimal("0.2000"));
            }
            if (hasOverheatedOpportunity()) {
                score = score.add(new BigDecimal("0.1200"));
            }
            return score.min(ONE).setScale(4, RoundingMode.HALF_UP);
        }

        private boolean isCycleHardRisk() {
            String text = describeCycle();
            return containsAny(text, "熊头", "熊中", "退潮", "杀跌", "亏钱效应扩散", "少做", "不参与");
        }

        private boolean hasMainlineRisk() {
            String text = describeMainline();
            boolean statusRisk = containsAny(text, "NON_MAIN", "DECLINE", "EBB_TIDE", "非主线", "衰退", "退潮", "不参与");
            boolean rearRisk = mainlineRearRisk != null && mainlineRearRisk.compareTo(new BigDecimal("0.5000")) >= 0;
            return statusRisk || rearRisk;
        }

        private boolean hasLeaderRisk() {
            String text = describeLeader();
            boolean statusRisk = containsAny(text, "DEAD", "DROPPED", "CHALLENGED", "掉队", "死亡", "被挑战");
            boolean repairRisk = leaderRepairScore != null && leaderRepairScore.compareTo(new BigDecimal("0.3500")) < 0;
            boolean challengeRisk = leaderChallengeRiskScore != null && leaderChallengeRiskScore.compareTo(new BigDecimal("0.6000")) >= 0;
            return statusRisk || repairRisk || challengeRisk;
        }

        private boolean hasDivergenceRisk() {
            String text = describeDivergence();
            boolean stateRisk = containsAny(text, "RECESSION_DIVERGENCE", "OVERHEATED_CONSENSUS", "退潮分歧", "一致过热");
            boolean brokenRisk = brokenLimitRiskScore != null && brokenLimitRiskScore.compareTo(new BigDecimal("0.4500")) >= 0;
            boolean highRisk = highPositionFeedbackScore != null && highPositionFeedbackScore.compareTo(new BigDecimal("0.4500")) >= 0;
            return stateRisk || brokenRisk || highRisk;
        }

        private boolean isDivergenceRecession() {
            return containsAny(describeDivergence(), "RECESSION_DIVERGENCE", "退潮分歧");
        }

        private boolean hasOverheatedOpportunity() {
            return containsAny(describeDivergence(), "ACCELERATED_CONSENSUS", "OVERHEATED_CONSENSUS", "加速一致", "一致过热");
        }

        private boolean hasContradictoryOpportunity() {
            String text = describeMainline() + "/" + describeDivergence();
            return containsAny(text, "可参与", "DIVERGENCE_TO_CONSENSUS", "分歧转一致")
                    && (isCycleHardRisk() || hasLeaderRisk());
        }

        private String describe() {
            return "周期[" + describeCycle() + "]，主线[" + describeMainline() + "]，龙头[" + describeLeader()
                    + "]，分歧一致[" + describeDivergence() + "]";
        }

        private String describeCycle() {
            return String.join("/", blankToDefault(cycleStage, ""), blankToDefault(emotionStage, ""),
                    blankToDefault(strategyBoundary, ""), blankToDefault(cycleConclusion, ""),
                    cycleConfidence == null ? "" : "confidence=" + cycleConfidence);
        }

        private String describeMainline() {
            return String.join("/", blankToDefault(mainlineStatus, ""), blankToDefault(mainlineLifecycle, ""),
                    blankToDefault(mainlineParticipation, ""), blankToDefault(mainlineConclusion, ""),
                    mainlineRearRisk == null ? "" : "rearRisk=" + mainlineRearRisk,
                    mainlineConfidence == null ? "" : "confidence=" + mainlineConfidence);
        }

        private String describeLeader() {
            return String.join("/", blankToDefault(leaderType, ""), blankToDefault(leaderStatus, ""),
                    blankToDefault(leaderConclusion, ""), leaderRepairScore == null ? "" : "repair=" + leaderRepairScore,
                    leaderDriveScore == null ? "" : "drive=" + leaderDriveScore,
                    leaderChallengeRiskScore == null ? "" : "challenge=" + leaderChallengeRiskScore,
                    leaderConfidence == null ? "" : "confidence=" + leaderConfidence);
        }

        private String describeDivergence() {
            return String.join("/", blankToDefault(divergenceState, ""), blankToDefault(divergenceConclusion, ""),
                    divergenceScore == null ? "" : "divergence=" + divergenceScore,
                    consensusScore == null ? "" : "consensus=" + consensusScore,
                    brokenLimitRiskScore == null ? "" : "brokenRisk=" + brokenLimitRiskScore,
                    highPositionFeedbackScore == null ? "" : "highFeedback=" + highPositionFeedbackScore,
                    divergenceConfidence == null ? "" : "confidence=" + divergenceConfidence);
        }
    }

    private record RiskDecision(RiskLevel riskLevel, RiskType riskType, BigDecimal riskScore,
                                BigDecimal confidence, String reason, String reduceSignal, String conclusion) {
    }
}
