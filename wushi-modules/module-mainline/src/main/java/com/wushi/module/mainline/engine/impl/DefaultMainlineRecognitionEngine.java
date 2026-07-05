package com.wushi.module.mainline.engine.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.MainlineStatus;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import com.wushi.module.mainline.engine.MainlineRecognitionEngine;
import com.wushi.module.mainline.factor.MainlineFactorCalculator;
import com.wushi.module.mainline.model.MainlineJudgementDetail;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.support.JudgementResultPostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultMainlineRecognitionEngine implements MainlineRecognitionEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final MainlineFactorCalculator mainlineFactorCalculator;
    private final JudgementResultPostProcessor resultPostProcessor;

    @Override
    public JudgementResult<MainlineJudgementDetail> judge(EngineRequest request) {
        FactorCalculateResult factorResult = mainlineFactorCalculator.calculate(toFactorRequest(request));
        MainlineDecision decision = decideMainline(factorResult.getFactorResults());
        String plateCode = defaultText(request.targetCode(), "AUTO");
        String plateName = defaultText(request.targetName(), "自动识别主线候选");
        String judgementId = "MAINLINE-" + request.tradeDate() + "-" + plateCode + "-" + UUID.randomUUID();

        JudgementResult<MainlineJudgementDetail> result = JudgementResult.<MainlineJudgementDetail>builder()
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .judgementMode(defaultMode(request.judgementMode()))
                .engineType(EngineType.MAINLINE)
                .targetType(defaultTargetType(request.targetType()))
                .targetCode(plateCode)
                .targetName(plateName)
                .conclusion(enrichConclusion(decision.conclusion(), request))
                .confidence(decision.confidence())
                .ruleVersion(request.ruleVersion())
                .dataQualityContext(request.dataQualityContext())
                .detail(new MainlineJudgementDetail(
                        plateCode,
                        plateName,
                        stringParam(request, "plateType", null),
                        intParam(request, "candidateRank"),
                        decimalParam(request, "candidateScore"),
                        stringParam(request, "candidateReason", null),
                        decision.mainlineStatus(),
                        value(factorResult.getFactorResults(), "MAINLINE_LIMIT_UP_COUNT"),
                        value(factorResult.getFactorResults(), "MAINLINE_ACTIVE_DAYS"),
                        value(factorResult.getFactorResults(), "MAINLINE_LADDER_INTEGRITY"),
                        value(factorResult.getFactorResults(), "MAINLINE_LEADER_QUALITY"),
                        value(factorResult.getFactorResults(), "MAINLINE_MIDDLE_ARMY_SUPPORT"),
                        value(factorResult.getFactorResults(), "MAINLINE_REAR_RISK"),
                        value(factorResult.getFactorResults(), "MAINLINE_CAPITAL_INFLOW"),
                        decision.satisfiedConditions(),
                        decision.unmetCondition(),
                        decision.tomorrowValidation(),
                        factorResult.getFactorResults()
                ))
                .evidenceList(factorResult.getEvidenceList())
                .conflictList(factorResult.getConflictList())
                .warningList(factorResult.getWarningList())
                .nextWatchList(buildNextWatchList(judgementId, request, plateCode, plateName, decision))
                .build();

        return resultPostProcessor.apply(request, result);
    }

    private FactorCalculateRequest toFactorRequest(EngineRequest request) {
        return FactorCalculateRequest.builder()
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .engineType(EngineType.MAINLINE)
                .targetCode(request.targetCode())
                .targetName(request.targetName())
                .ruleContext(request.ruleContext())
                .facts(request.params())
                .params(request.params())
                .build();
    }

    private MainlineDecision decideMainline(List<FactorResult> factors) {
        List<String> satisfied = new ArrayList<>();
        List<String> unmet = new ArrayList<>();

        collectCondition(factors, "MAINLINE_ACTIVE_DAYS", "连续活跃", satisfied, unmet);
        collectCondition(factors, "MAINLINE_LIMIT_UP_COUNT", "涨停强度", satisfied, unmet);
        collectCondition(factors, "MAINLINE_LADDER_INTEGRITY", "梯队完整", satisfied, unmet);
        collectCondition(factors, "MAINLINE_LEADER_QUALITY", "龙头质量", satisfied, unmet);
        collectCondition(factors, "MAINLINE_MIDDLE_ARMY_SUPPORT", "中军承接", satisfied, unmet);
        collectCondition(factors, "MAINLINE_REAR_RISK", "后排风险可控", satisfied, unmet);
        collectCondition(factors, "MAINLINE_CAPITAL_INFLOW", "资金净流入", satisfied, unmet);

        boolean active = passed(factors, "MAINLINE_ACTIVE_DAYS");
        boolean limitUp = passed(factors, "MAINLINE_LIMIT_UP_COUNT");
        boolean ladder = passed(factors, "MAINLINE_LADDER_INTEGRITY");
        boolean leader = passed(factors, "MAINLINE_LEADER_QUALITY");
        boolean middleArmy = passed(factors, "MAINLINE_MIDDLE_ARMY_SUPPORT");
        boolean rearRiskControlled = passed(factors, "MAINLINE_REAR_RISK");
        boolean capital = passed(factors, "MAINLINE_CAPITAL_INFLOW");
        BigDecimal score = score(factors);
        int conflictCount = countEvidence(factors, com.wushi.common.enums.EvidenceType.CONFLICT);
        int warningCount = countEvidence(factors, com.wushi.common.enums.EvidenceType.WARNING);
        BigDecimal confidence = confidence(score, conflictCount, warningCount);

        if (active && limitUp && ladder && leader && middleArmy && rearRiskControlled) {
            return new MainlineDecision(
                    MainlineStatus.MAIN,
                    confidence,
                    "主线确认候选",
                    String.join("、", satisfied),
                    String.join("、", unmet),
                    "明日验证龙头是否继续高开或弱转强，中军是否继续承接，后排炸板率是否下降。",
                    "板块已满足连续活跃、涨停强度、梯队完整、龙头质量、中军承接和后排风险可控，进入主线确认候选。"
            );
        }
        if ((active && limitUp && ladder) || (limitUp && leader && capital)) {
            return new MainlineDecision(
                    MainlineStatus.CANDIDATE,
                    confidence,
                    "主线候选",
                    String.join("、", satisfied),
                    String.join("、", unmet),
                    "明日验证是否补齐中军承接、龙头分歧修复和后排风险收敛。",
                    "板块已有主线雏形，但仍有关键条件未满足，不能直接当作确认主线。"
            );
        }
        if (limitUp || capital) {
            return new MainlineDecision(
                    MainlineStatus.NEW_THEME,
                    confidence,
                    "新题材观察",
                    String.join("、", satisfied),
                    String.join("、", unmet),
                    "明日验证是否连续活跃、是否出现梯队，以及资金是否继续聚焦。",
                    "板块出现点火或资金流入，但持续性与结构尚不足，先按新题材观察。"
            );
        }
        return new MainlineDecision(
                MainlineStatus.NON_MAIN,
                confidence,
                "非主线",
                String.join("、", satisfied),
                String.join("、", unmet),
                "明日观察是否有新增涨停梯队和资金回流，否则继续按轮动噪音处理。",
                "板块缺少持续性、涨停梯队和资金合力，暂不具备可参与主线意义。"
        );
    }

    private void collectCondition(List<FactorResult> factors, String factorCode, String conditionName,
                                  List<String> satisfied, List<String> unmet) {
        if (passed(factors, factorCode)) {
            satisfied.add(conditionName);
        } else {
            unmet.add(conditionName);
        }
    }

    private List<NextWatchItem> buildNextWatchList(String judgementId, EngineRequest request, String plateCode,
                                                   String plateName, MainlineDecision decision) {
        LocalDate watchDate = request.tradeDate() == null ? null : request.tradeDate().plusDays(1);
        return List.of(
                watch(judgementId, request, watchDate, plateCode, plateName, "MAINLINE_WATCH_LEADER",
                        "验证龙头是否继续带动板块", "龙头高开、弱转强或分歧回封，板块涨停数量不明显回落",
                        "龙头继续带动，支持主线确认或强化", "龙头断板无修复且板块跟随走弱，主线候选降级", 1),
                watch(judgementId, request, watchDate, plateCode, plateName, "MAINLINE_WATCH_MIDDLE_ARMY",
                        "验证中军承接是否健康", "板块成交额保持，核心中军不破位且资金不大幅流出",
                        "中军承接健康，说明主线具备容量", "中军放量滞涨或资金流出，说明合力不足", 2),
                watch(judgementId, request, watchDate, plateCode, plateName, "MAINLINE_WATCH_REAR_RISK",
                        "验证后排风险是否收敛", "后排炸板率下降，补涨股不出现大面扩散",
                        "后排修复，支持分歧后再一致", "后排继续炸板或亏钱扩散，主线风险上升", 3)
        );
    }

    private NextWatchItem watch(String judgementId, EngineRequest request, LocalDate watchDate, String plateCode,
                                String plateName, String watchCode, String title, String condition,
                                String expectedSignal, String riskSignal, int priority) {
        return NextWatchItem.builder()
                .watchId(watchCode + "-" + request.tradeDate() + "-" + plateCode)
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .watchDate(watchDate)
                .engineType(EngineType.MAINLINE)
                .targetType(TargetType.PLATE)
                .targetCode(plateCode)
                .targetName(plateName)
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

    private boolean passed(List<FactorResult> factors, String factorCode) {
        return factors.stream()
                .filter(factor -> factorCode.equals(factor.getFactorCode()))
                .findFirst()
                .map(FactorResult::getThresholdPassed)
                .orElse(false);
    }

    private BigDecimal value(List<FactorResult> factors, String factorCode) {
        return factors.stream()
                .filter(factor -> factorCode.equals(factor.getFactorCode()))
                .findFirst()
                .map(FactorResult::getFactorValue)
                .orElse(null);
    }

    private int countEvidence(List<FactorResult> factors, com.wushi.common.enums.EvidenceType evidenceType) {
        return (int) factors.stream()
                .filter(factor -> factor.getEvidenceType() == evidenceType)
                .count();
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
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return weightedScore.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal confidence(BigDecimal score, int conflictCount, int warningCount) {
        BigDecimal penalty = BigDecimal.valueOf(conflictCount).multiply(new BigDecimal("0.0500"))
                .add(BigDecimal.valueOf(warningCount).multiply(new BigDecimal("0.0300")));
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

    private JudgementMode defaultMode(JudgementMode judgementMode) {
        return judgementMode == null ? JudgementMode.REALTIME : judgementMode;
    }

    private TargetType defaultTargetType(TargetType targetType) {
        return targetType == null ? TargetType.PLATE : targetType;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String enrichConclusion(String conclusion, EngineRequest request) {
        Integer rank = intParam(request, "candidateRank");
        String reason = stringParam(request, "candidateReason", null);
        if (rank == null && (reason == null || reason.isBlank())) {
            return conclusion;
        }
        String rankText = rank == null ? "候选排序未定" : "候选排名第" + rank;
        String reasonText = reason == null || reason.isBlank() ? "" : "；" + reason;
        return conclusion + "（" + rankText + reasonText + "）";
    }

    private String stringParam(EngineRequest request, String key, String defaultValue) {
        if (request.params() == null || request.params().get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(request.params().get(key));
    }

    private Integer intParam(EngineRequest request, String key) {
        if (request.params() == null || request.params().get(key) == null) {
            return null;
        }
        Object value = request.params().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private BigDecimal decimalParam(EngineRequest request, String key) {
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

    private record MainlineDecision(
            MainlineStatus mainlineStatus,
            BigDecimal confidence,
            String transitionSignal,
            String satisfiedConditionText,
            String unmetCondition,
            String tomorrowValidation,
            String conclusion,
            List<String> satisfiedConditions
    ) {
        private MainlineDecision(MainlineStatus mainlineStatus, BigDecimal confidence, String transitionSignal,
                                 String satisfiedConditionText, String unmetCondition,
                                 String tomorrowValidation, String conclusion) {
            this(mainlineStatus, confidence, transitionSignal, satisfiedConditionText, unmetCondition,
                    tomorrowValidation, conclusion, toList(satisfiedConditionText));
        }

        private static List<String> toList(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            return List.of(text.split("、"));
        }
    }
}
