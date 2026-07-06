package com.wushi.module.emotion.engine.impl;

import com.wushi.common.enums.EmotionCycleStage;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.MarketCycleStage;
import com.wushi.common.enums.TargetType;
import com.wushi.common.enums.WatchValidationStatus;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.NextWatchItem;
import com.wushi.module.emotion.engine.CycleRecognitionEngine;
import com.wushi.module.emotion.factor.CycleFactorCalculator;
import com.wushi.module.emotion.model.CycleJudgementDetail;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.support.JudgementResultPostProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultCycleRecognitionEngine implements CycleRecognitionEngine {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final CycleFactorCalculator cycleFactorCalculator;
    private final JudgementResultPostProcessor resultPostProcessor;

    @Override
    public JudgementResult<CycleJudgementDetail> judge(EngineRequest request) {
        FactorCalculateResult factorResult = cycleFactorCalculator.calculate(toFactorRequest(request));
        CycleStageDecision decision = decideStage(factorResult.getFactorResults());
        String judgementId = "CYCLE-" + request.tradeDate() + "-" + UUID.randomUUID();

        JudgementResult<CycleJudgementDetail> result = JudgementResult.<CycleJudgementDetail>builder()
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .judgementMode(defaultMode(request.judgementMode()))
                .engineType(EngineType.CYCLE)
                .targetType(defaultTargetType(request.targetType()))
                .targetCode(defaultText(request.targetCode(), "MARKET"))
                .targetName(defaultText(request.targetName(), "全市场"))
                .conclusion(decision.conclusion())
                .confidence(decision.confidence())
                .ruleVersion(request.ruleVersion())
                .dataQualityContext(request.dataQualityContext())
                .detail(new CycleJudgementDetail(
                        decision.marketCycleStage(),
                        decision.emotionCycleStage(),
                        value(factorResult.getFactorResults(), "CYCLE_MONEY_EFFECT_SCORE"),
                        value(factorResult.getFactorResults(), "CYCLE_LOSS_EFFECT_SCORE"),
                        decision.stageScore(),
                        decision.transitionSignal(),
                        decision.stageReason(),
                        decision.strategyBoundary(),
                        decision.allowedMode(),
                        decision.falseSignalRisk(),
                        factorResult.getFactorResults()
                ))
                .evidenceList(factorResult.getEvidenceList())
                .conflictList(factorResult.getConflictList())
                .warningList(factorResult.getWarningList())
                .nextWatchList(buildNextWatchList(judgementId, request, decision))
                .build();

        return resultPostProcessor.apply(request, result);
    }

    private FactorCalculateRequest toFactorRequest(EngineRequest request) {
        return FactorCalculateRequest.builder()
                .tradeDate(request.tradeDate())
                .asOfDate(request.asOfDate())
                .engineType(EngineType.CYCLE)
                .targetCode(defaultText(request.targetCode(), "MARKET"))
                .targetName(defaultText(request.targetName(), "全市场"))
                .ruleContext(request.ruleContext())
                .facts(request.params())
                .params(request.params())
                .build();
    }

    private CycleStageDecision decideStage(List<FactorResult> factors) {
        int supportCount = countPassedSupport(factors);
        int conflictCount = countEvidence(factors, com.wushi.common.enums.EvidenceType.CONFLICT);
        int warningCount = countEvidence(factors, com.wushi.common.enums.EvidenceType.WARNING);

        boolean moneyStrong = passed(factors, "CYCLE_MONEY_EFFECT_SCORE");
        boolean lossControlled = passed(factors, "CYCLE_LOSS_EFFECT_SCORE");
        boolean widthStrong = passed(factors, "CYCLE_ABOVE_MA20_RATIO");
        boolean limitUpStrong = passed(factors, "CYCLE_LIMIT_UP_COUNT");
        boolean limitDownControlled = passed(factors, "CYCLE_LIMIT_DOWN_COUNT");
        boolean brokenControlled = passed(factors, "CYCLE_BROKEN_LIMIT_COUNT");

        BigDecimal stageScore = score(factors);
        BigDecimal confidence = confidence(stageScore, conflictCount, warningCount);

        if (!lossControlled && !limitDownControlled && !moneyStrong) {
            return new CycleStageDecision(
                    MarketCycleStage.BEAR_MIDDLE,
                    EmotionCycleStage.RECESSION_KILL,
                    stageScore,
                    confidence,
                    "退潮杀跌",
                    "亏钱效应与跌停负反馈同步扩散，赚钱效应不足，周期边界应先收缩。",
                    "市场处于退潮杀跌，先看风险是否释放，而不是寻找个股机会。",
                    "周期不支持主动进攻，策略边界是防守、降频、等待亏钱效应衰竭。",
                    "空仓/轻仓观察，高位股和补涨股不参与，只记录风险兑现样本。",
                    "若只出现局部反抽但跌停、高位负反馈未收敛，属于假修复。"
            );
        }
        if (!lossControlled && (warningCount > 0 || !brokenControlled)) {
            return new CycleStageDecision(
                    MarketCycleStage.BULL_END,
                    EmotionCycleStage.HIGH_POSITION_DIVERGENCE,
                    stageScore,
                    confidence,
                    "高位分歧",
                    "赚钱效应可能仍在，但亏钱效应或炸板分歧已经抬头，需要警惕一致后的兑现。",
                    "市场进入高位分歧，重点验证核心股能否修复，以及后排是否继续亏钱。",
                    "周期边界从进攻转为控仓，只有核心主线分歧转一致才有观察价值。",
                    "只看龙头分歧修复和主线核心，回避后排补涨和明牌一致。",
                    "高位一致后继续缩量加速、后排大面增加，是牛末/退潮的假强信号。"
            );
        }
        if (moneyStrong && widthStrong && limitUpStrong && lossControlled && limitDownControlled && brokenControlled) {
            return new CycleStageDecision(
                    MarketCycleStage.BULL_START,
                    EmotionCycleStage.PROFIT_EFFECT_SPREAD,
                    stageScore,
                    confidence,
                    "赚钱效应扩散",
                    "涨停、赚钱效应、市场宽度与亏钱效应收敛形成共振，市场从修复向强周期推进。",
                    "市场赚钱效应扩散，适合继续观察主线是否确认和龙头是否竞争上位。",
                    "周期支持试错到进攻，但个股必须服从主线和龙头地位。",
                    "可参与主线确认、龙头上位、趋势核心和健康分歧转一致。",
                    "若主线未确认、后排先高潮，容易形成假扩散。"
            );
        }
        if (moneyStrong && lossControlled && supportCount >= 3) {
            return new CycleStageDecision(
                    MarketCycleStage.REPAIR,
                    EmotionCycleStage.WEAK_REPAIR,
                    stageScore,
                    confidence,
                    "弱修复",
                    "赚钱效应开始恢复，亏钱效应相对可控，但宽度或涨停结构尚未全面共振。",
                    "市场处于修复期，重点看题材能否从轮动走向持续主线。",
                    "周期允许低强度试错，但必须等待主线持续性和亏钱效应继续收敛。",
                    "轻仓观察点火/发酵主线，优先看龙头候选和中军承接。",
                    "一日游轮动、次日无承接，是假修复。"
            );
        }
        if (lossControlled && !moneyStrong) {
            return new CycleStageDecision(
                    MarketCycleStage.BEAR_END,
                    EmotionCycleStage.PANIC_ICE_POINT,
                    stageScore,
                    confidence,
                    "恐慌衰竭",
                    "亏钱效应开始收敛，但赚钱效应尚未恢复，可能是熊末或真冰点观察区。",
                    "市场可能处于冰点衰竭，明日需要验证资金是否回流和涨停质量是否提升。",
                    "周期仍偏防守，只能观察真冰点是否成立。",
                    "只记录恐慌释放、政策修复、妖股活跃和第一批回流方向。",
                    "下跌中继里的弱反弹是假冰点，必须看资金回流和亏钱效应衰竭。"
            );
        }
        return new CycleStageDecision(
                MarketCycleStage.UNKNOWN,
                EmotionCycleStage.UNKNOWN,
                stageScore,
                confidence,
                "混沌观察",
                "周期证据尚未形成清晰共振，支持与冲突并存，不宜输出绝对判断。",
                "市场周期处于混沌观察，等待赚钱效应、亏钱效应和宽度进一步确认。",
                "周期边界不清晰，策略必须降低确定性假设。",
                "只做观察和复盘，不把局部强度外推成系统机会。",
                "支持与冲突并存时，任何单一指标走强都可能是假信号。"
        );
    }

    private List<NextWatchItem> buildNextWatchList(String judgementId, EngineRequest request, CycleStageDecision decision) {
        LocalDate watchDate = request.tradeDate() == null ? null : request.tradeDate().plusDays(1);
        return List.of(
                watch(judgementId, request, watchDate, "CYCLE_WATCH_MONEY_EFFECT", "验证赚钱效应是否延续",
                        "CYCLE_MONEY_EFFECT_SCORE >= 当前规则阈值且涨停家数不明显回落",
                        "赚钱效应延续，支持修复或强周期继续推进",
                        "赚钱效应回落且炸板升高，说明修复可能失败", 1),
                watch(judgementId, request, watchDate, "CYCLE_WATCH_LOSS_EFFECT", "验证亏钱效应是否扩散",
                        "CYCLE_LOSS_EFFECT_SCORE <= 当前规则阈值且跌停家数不扩散",
                        "亏钱效应收敛，支持风险释放后修复",
                        "跌停和高位负反馈继续增加，说明退潮风险兑现", 2),
                watch(judgementId, request, watchDate, "CYCLE_WATCH_WIDTH", "验证市场宽度是否改善",
                        "CYCLE_ABOVE_MA20_RATIO >= 当前规则阈值或持续回升",
                        "宽度改善，说明行情土壤变好",
                        "宽度继续走弱，题材轮动更可能是一日游", 3)
        );
    }

    private NextWatchItem watch(String judgementId, EngineRequest request, LocalDate watchDate, String watchCode,
                                String title, String condition, String expectedSignal, String riskSignal, int priority) {
        return NextWatchItem.builder()
                .watchId(watchCode + "-" + request.tradeDate())
                .judgementId(judgementId)
                .tradeDate(request.tradeDate())
                .watchDate(watchDate)
                .engineType(EngineType.CYCLE)
                .targetType(defaultTargetType(request.targetType()))
                .targetCode(defaultText(request.targetCode(), "MARKET"))
                .targetName(defaultText(request.targetName(), "全市场"))
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

    private int countPassedSupport(List<FactorResult> factors) {
        return (int) factors.stream()
                .filter(factor -> Boolean.TRUE.equals(factor.getThresholdPassed()))
                .count();
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

    private BigDecimal confidence(BigDecimal stageScore, int conflictCount, int warningCount) {
        BigDecimal penalty = BigDecimal.valueOf(conflictCount).multiply(new BigDecimal("0.0600"))
                .add(BigDecimal.valueOf(warningCount).multiply(new BigDecimal("0.0300")));
        BigDecimal confidence = stageScore.multiply(new BigDecimal("0.7000"))
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
        return targetType == null ? TargetType.MARKET : targetType;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record CycleStageDecision(
            MarketCycleStage marketCycleStage,
            EmotionCycleStage emotionCycleStage,
            BigDecimal stageScore,
            BigDecimal confidence,
            String transitionSignal,
            String stageReason,
            String conclusion,
            String strategyBoundary,
            String allowedMode,
            String falseSignalRisk
    ) {
    }
}
