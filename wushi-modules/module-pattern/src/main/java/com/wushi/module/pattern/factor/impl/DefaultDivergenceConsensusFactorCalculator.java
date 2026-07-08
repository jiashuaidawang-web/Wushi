package com.wushi.module.pattern.factor.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.FactorResult;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.pattern.factor.DivergenceConsensusFactorCalculator;
import com.wushi.module.rule.factor.AbstractFactorCalculator;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.factor.support.FactorEvidenceConverter;
import com.wushi.module.rule.factor.support.ThresholdMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class DefaultDivergenceConsensusFactorCalculator extends AbstractFactorCalculator implements DivergenceConsensusFactorCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
    private static final String CALCULATOR_CODE = "DIVERGENCE_CONSENSUS_FACTOR_CALCULATOR";
    private static final String SOURCE_LIMIT = "stock_limit_status_daily";
    private static final String SOURCE_EVENT = "stock_limit_intraday_event";
    private static final String SOURCE_PLATE = "plate_daily_snapshot";
    private static final String SOURCE_KLINE = "stock_daily_kline";
    private static final String SOURCE_HIGH_POSITION = "high_position_feedback_daily";
    private static final List<String> FACTOR_CODES = List.of(
            "PATTERN_DIVERGENCE_SCORE",
            "PATTERN_CONSENSUS_SCORE",
            "PATTERN_REFILL_QUALITY",
            "PATTERN_BROKEN_LIMIT_RISK",
            "PATTERN_REAR_FEEDBACK",
            "PATTERN_TURNOVER_ACCEPTANCE",
            "PATTERN_SHRINK_ACCELERATION",
            "PATTERN_HIGH_POSITION_FEEDBACK"
    );

    private final MarketFactService marketFactService;

    public DefaultDivergenceConsensusFactorCalculator(
            ThresholdMatcher thresholdMatcher,
            FactorEvidenceConverter evidenceConverter,
            MarketFactService marketFactService
    ) {
        super(thresholdMatcher, evidenceConverter);
        this.marketFactService = marketFactService;
    }

    @Override
    public String calculatorCode() {
        return CALCULATOR_CODE;
    }

    @Override
    public List<String> supportFactorCodes() {
        return FACTOR_CODES;
    }

    @Override
    public FactorCalculateResult calculate(FactorCalculateRequest request) {
        if (request.getEngineType() != null && request.getEngineType() != EngineType.DIVERGENCE_CONSENSUS) {
            throw new IllegalArgumentException("Divergence consensus factor calculator only supports DIVERGENCE_CONSENSUS engine");
        }

        PatternFacts facts = resolveFacts(request);
        String sourceKey = "tradeDate=" + request.getTradeDate() + ",target=" + defaultText(facts.targetCode(), "MARKET");

        BigDecimal brokenLimitRisk = resolveBrokenLimitRisk(facts);
        BigDecimal refillQuality = resolveRefillQuality(facts);
        BigDecimal rearFeedback = resolveRearFeedback(facts);
        BigDecimal turnoverAcceptance = resolveTurnoverAcceptance(facts);
        BigDecimal shrinkAcceleration = resolveShrinkAcceleration(facts);
        BigDecimal highPositionFeedback = resolveHighPositionFeedback(facts);
        BigDecimal divergence = resolveDivergenceScore(facts, brokenLimitRisk, turnoverAcceptance, highPositionFeedback);
        BigDecimal consensus = resolveConsensusScore(facts, refillQuality, rearFeedback, shrinkAcceleration, brokenLimitRisk, highPositionFeedback);

        List<FactorResult> factors = new ArrayList<>();
        factors.add(buildFactor(request, "PATTERN_DIVERGENCE_SCORE", "分歧强度", divergence, SOURCE_EVENT, sourceKey,
                "炸板、开板、换手放大、后排掉队形成的分歧强度。"));
        factors.add(buildFactor(request, "PATTERN_CONSENSUS_SCORE", "一致强度", consensus, SOURCE_PLATE, sourceKey,
                "板块内龙头、中军、后排同步修复形成的一致强度。"));
        factors.add(buildFactor(request, "PATTERN_REFILL_QUALITY", "回封质量", refillQuality, SOURCE_EVENT, sourceKey,
                "开板后的回封速度、封单恢复、回封后稳定性。"));
        factors.add(buildFactor(request, "PATTERN_BROKEN_LIMIT_RISK", "炸板风险", brokenLimitRisk, SOURCE_LIMIT, sourceKey,
                "炸板率和炸板后回落幅度，衡量一致失败风险。"));
        factors.add(buildFactor(request, "PATTERN_REAR_FEEDBACK", "后排反馈", rearFeedback, SOURCE_PLATE, sourceKey,
                "后排是否修复、是否继续补涨，验证主线分歧是否被承接。"));
        factors.add(buildFactor(request, "PATTERN_TURNOVER_ACCEPTANCE", "换手承接", turnoverAcceptance, SOURCE_KLINE, sourceKey,
                "分歧时成交额、换手率和振幅是否表现为良性承接，而不是失控抛压。"));
        factors.add(buildFactor(request, "PATTERN_SHRINK_ACCELERATION", "缩量加速", shrinkAcceleration, SOURCE_KLINE, sourceKey,
                "封单稳定、开板少、成交缩量且涨幅保持强势，衡量一致阶段加速程度。"));
        factors.add(buildFactor(request, "PATTERN_HIGH_POSITION_FEEDBACK", "高位反馈", highPositionFeedback, SOURCE_HIGH_POSITION, sourceKey,
                "高位断板、大阴、跌停和炸板失败等负反馈，判断分歧是否兑现为退潮。"));

        return assemble(CALCULATOR_CODE, request.getRuleContext(), factors);
    }

    private PatternFacts resolveFacts(FactorCalculateRequest request) {
        String requestedPlateCode = stringParam(request, "plateCode", null);
        String requestedTargetCode = request.getTargetCode();
        String targetCode = StringUtils.hasText(requestedPlateCode)
                ? requestedPlateCode
                : "MARKET".equalsIgnoreCase(defaultText(requestedTargetCode, "")) ? null : requestedTargetCode;
        String targetName = defaultText(request.getTargetName(), stringParam(request, "plateName", null));
        Set<String> scopedStocks = resolveScopedStocks(request, targetCode);
        boolean scopedPlateWithoutMembers = StringUtils.hasText(targetCode) && scopedStocks.isEmpty();
        Map<String, Object> plateRow = resolvePlateRow(request, targetCode);
        List<Map<String, Object>> limitRows = scopedPlateWithoutMembers ? List.of() : filterByScope(safeRows(FactTable.STOCK_LIMIT_STATUS_DAILY, request), scopedStocks);
        List<Map<String, Object>> eventRows = scopedPlateWithoutMembers ? List.of() : filterByScope(safeRows(FactTable.STOCK_LIMIT_INTRADAY_EVENT, request), scopedStocks);
        List<Map<String, Object>> klineRows = scopedPlateWithoutMembers ? List.of() : filterByScope(safeRows(FactTable.STOCK_DAILY_KLINE, request), scopedStocks);
        List<Map<String, Object>> highPositionRows = filterHighPositionRows(safeRows(FactTable.HIGH_POSITION_FEEDBACK_DAILY, request), targetCode);
        return new PatternFacts(targetCode, targetName, scopedStocks, plateRow, limitRows, eventRows, klineRows, highPositionRows);
    }

    private Set<String> resolveScopedStocks(FactorCalculateRequest request, String plateCode) {
        if (!StringUtils.hasText(plateCode)) {
            return Set.of();
        }
        Set<String> stockCodes = new HashSet<>();
        for (Map<String, Object> relation : safeRows(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, request)) {
            if (plateCode.equals(text(relation.get("plate_code"), ""))) {
                String stockCode = text(relation.get("stock_code"), "");
                if (StringUtils.hasText(stockCode)) {
                    stockCodes.add(stockCode);
                }
            }
        }
        return stockCodes;
    }

    private Map<String, Object> resolvePlateRow(FactorCalculateRequest request, String plateCode) {
        if (!StringUtils.hasText(plateCode)) {
            return Map.of();
        }
        return safeRows(FactTable.PLATE_DAILY_SNAPSHOT, request).stream()
                .filter(row -> plateCode.equals(text(row.get("plate_code"), "")))
                .findFirst()
                .orElse(Map.of());
    }

    private BigDecimal resolveBrokenLimitRisk(PatternFacts facts) {
        BigDecimal broken = BigDecimal.valueOf(countStatus(facts.limitRows(), "BROKEN_LIMIT") + countStatus(facts.limitRows(), "OPEN_LIMIT"));
        BigDecimal limitUp = BigDecimal.valueOf(countStatus(facts.limitRows(), "LIMIT_UP"));
        BigDecimal touch = broken.add(limitUp);
        BigDecimal eventOpen = BigDecimal.valueOf(countEvent(facts.eventRows(), "OPEN_LIMIT") + countEvent(facts.eventRows(), "BROKEN_LIMIT"));
        BigDecimal openTimes = facts.limitRows().stream()
                .map(row -> defaultZero(rowDecimal(row, "open_limit_times")))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal rowRisk = touch.compareTo(BigDecimal.ZERO) <= 0 ? ZERO : cap(broken.divide(touch, 4, RoundingMode.HALF_UP));
        BigDecimal eventRisk = cap(eventOpen.divide(BigDecimal.valueOf(Math.max(facts.eventRows().size(), 1)), 4, RoundingMode.HALF_UP));
        BigDecimal openRisk = cap(openTimes.divide(BigDecimal.valueOf(Math.max(facts.limitRows().size() * 3L, 1L)), 4, RoundingMode.HALF_UP));
        return rowRisk.multiply(new BigDecimal("0.5500"))
                .add(eventRisk.multiply(new BigDecimal("0.2500")))
                .add(openRisk.multiply(new BigDecimal("0.2000")))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveRefillQuality(PatternFacts facts) {
        long openCount = countEvent(facts.eventRows(), "OPEN_LIMIT") + countEvent(facts.eventRows(), "BROKEN_LIMIT");
        long refillCount = countEvent(facts.eventRows(), "REFILL_LIMIT") + countEvent(facts.eventRows(), "SEAL_LIMIT");
        BigDecimal refillRatio = openCount <= 0
                ? (refillCount > 0 ? new BigDecimal("0.6500") : ZERO)
                : cap(BigDecimal.valueOf(refillCount).divide(BigDecimal.valueOf(openCount), 4, RoundingMode.HALF_UP));
        BigDecimal sealRecovery = averageSealScore(facts.eventRows());
        BigDecimal speed = refillSpeedScore(facts.eventRows());
        BigDecimal dailyProxy = dailyRefillProxy(facts.limitRows());
        return refillRatio.multiply(new BigDecimal("0.4000"))
                .add(sealRecovery.multiply(new BigDecimal("0.2500")))
                .add(speed.multiply(new BigDecimal("0.2000")))
                .add(dailyProxy.multiply(new BigDecimal("0.1500")))
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveRearFeedback(PatternFacts facts) {
        if (!facts.plateRow().isEmpty()) {
            BigDecimal up = defaultZero(rowDecimal(facts.plateRow(), "up_count"));
            BigDecimal down = defaultZero(rowDecimal(facts.plateRow(), "down_count"));
            BigDecimal limitUp = defaultZero(rowDecimal(facts.plateRow(), "limit_up_count"));
            BigDecimal limitDown = defaultZero(rowDecimal(facts.plateRow(), "limit_down_count"));
            BigDecimal broken = defaultZero(rowDecimal(facts.plateRow(), "broken_limit_count"));
            BigDecimal median = defaultZero(rowDecimal(facts.plateRow(), "median_change_pct"));
            BigDecimal breadth = up.add(down).compareTo(BigDecimal.ZERO) <= 0
                    ? ZERO
                    : cap(up.divide(up.add(down), 4, RoundingMode.HALF_UP));
            BigDecimal limitSpread = cap(limitUp.divide(new BigDecimal("10"), 4, RoundingMode.HALF_UP));
            BigDecimal medianScore = cap(median.max(BigDecimal.ZERO).divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP));
            BigDecimal riskPenalty = cap(limitDown.add(broken).divide(limitUp.add(limitDown).add(broken).max(BigDecimal.ONE), 4, RoundingMode.HALF_UP));
            return breadth.multiply(new BigDecimal("0.3500"))
                    .add(limitSpread.multiply(new BigDecimal("0.2500")))
                    .add(medianScore.multiply(new BigDecimal("0.2500")))
                    .add(BigDecimal.ONE.subtract(riskPenalty).multiply(new BigDecimal("0.1500")))
                    .min(ONE)
                    .setScale(4, RoundingMode.HALF_UP);
        }
        long upLimit = countStatus(facts.limitRows(), "LIMIT_UP");
        long broken = countStatus(facts.limitRows(), "BROKEN_LIMIT") + countStatus(facts.limitRows(), "OPEN_LIMIT");
        long limitDown = countStatus(facts.limitRows(), "LIMIT_DOWN");
        BigDecimal support = cap(BigDecimal.valueOf(upLimit).divide(new BigDecimal("60"), 4, RoundingMode.HALF_UP));
        BigDecimal riskPenalty = cap(BigDecimal.valueOf(broken + limitDown).divide(BigDecimal.valueOf(Math.max(facts.limitRows().size(), 1)), 4, RoundingMode.HALF_UP));
        return support.multiply(new BigDecimal("0.7000"))
                .add(BigDecimal.ONE.subtract(riskPenalty).multiply(new BigDecimal("0.3000")))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTurnoverAcceptance(PatternFacts facts) {
        if (facts.klineRows().isEmpty()) {
            return ZERO;
        }
        BigDecimal avgTurnover = average(facts.klineRows(), "turnover_rate");
        BigDecimal avgAmount = average(facts.klineRows(), "amount");
        BigDecimal avgAmplitude = average(facts.klineRows(), "amplitude");
        BigDecimal turnoverScore = bandScore(avgTurnover, new BigDecimal("5"), new BigDecimal("18"), new BigDecimal("35"));
        BigDecimal amountScore = cap(avgAmount.divide(new BigDecimal("3000000000"), 4, RoundingMode.HALF_UP));
        BigDecimal amplitudeScore = BigDecimal.ONE.subtract(cap(avgAmplitude.subtract(new BigDecimal("4")).max(ZERO).divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP)));
        return turnoverScore.multiply(new BigDecimal("0.4500"))
                .add(amountScore.multiply(new BigDecimal("0.3000")))
                .add(amplitudeScore.multiply(new BigDecimal("0.2500")))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveShrinkAcceleration(PatternFacts facts) {
        BigDecimal sealScore = averageSealScore(facts.limitRows());
        BigDecimal lowOpenScore = BigDecimal.ONE.subtract(cap(average(facts.limitRows(), "open_limit_times").divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP)));
        BigDecimal changeScore = cap(average(facts.klineRows(), "change_pct").max(ZERO).divide(new BigDecimal("10"), 4, RoundingMode.HALF_UP));
        BigDecimal amountScore = facts.klineRows().isEmpty()
                ? ZERO
                : BigDecimal.ONE.subtract(cap(average(facts.klineRows(), "amount").divide(new BigDecimal("12000000000"), 4, RoundingMode.HALF_UP)));
        return sealScore.multiply(new BigDecimal("0.3500"))
                .add(lowOpenScore.multiply(new BigDecimal("0.2500")))
                .add(changeScore.multiply(new BigDecimal("0.2500")))
                .add(amountScore.multiply(new BigDecimal("0.1500")))
                .max(ZERO)
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveHighPositionFeedback(PatternFacts facts) {
        if (facts.highPositionRows().isEmpty()) {
            return ZERO;
        }
        BigDecimal impact = average(facts.highPositionRows(), "impact_score");
        BigDecimal countScore = cap(BigDecimal.valueOf(facts.highPositionRows().size()).divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP));
        BigDecimal severeScore = cap(BigDecimal.valueOf(facts.highPositionRows().stream()
                .filter(row -> isSevereFeedback(normalize(row.get("feedback_type"))))
                .count()).divide(BigDecimal.valueOf(Math.max(facts.highPositionRows().size(), 1)), 4, RoundingMode.HALF_UP));
        return normalizeScore(impact).multiply(new BigDecimal("0.5000"))
                .add(countScore.multiply(new BigDecimal("0.2500")))
                .add(severeScore.multiply(new BigDecimal("0.2500")))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDivergenceScore(PatternFacts facts, BigDecimal brokenLimitRisk,
                                              BigDecimal turnoverAcceptance, BigDecimal highPositionFeedback) {
        BigDecimal openEventScore = cap(BigDecimal.valueOf(countEvent(facts.eventRows(), "OPEN_LIMIT") + countEvent(facts.eventRows(), "BROKEN_LIMIT"))
                .divide(BigDecimal.valueOf(Math.max(facts.eventRows().size(), 1)), 4, RoundingMode.HALF_UP));
        BigDecimal turnoverDivergence = turnoverAcceptance.compareTo(new BigDecimal("0.7500")) > 0
                ? turnoverAcceptance
                : ZERO;
        return brokenLimitRisk.multiply(new BigDecimal("0.4200"))
                .add(openEventScore.multiply(new BigDecimal("0.2200")))
                .add(turnoverDivergence.multiply(new BigDecimal("0.1800")))
                .add(highPositionFeedback.multiply(new BigDecimal("0.1800")))
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveConsensusScore(PatternFacts facts, BigDecimal refillQuality, BigDecimal rearFeedback,
                                             BigDecimal shrinkAcceleration, BigDecimal brokenLimitRisk,
                                             BigDecimal highPositionFeedback) {
        BigDecimal limitStrength = cap(BigDecimal.valueOf(countStatus(facts.limitRows(), "LIMIT_UP"))
                .divide(StringUtils.hasText(facts.targetCode()) ? new BigDecimal("8") : new BigDecimal("80"), 4, RoundingMode.HALF_UP));
        BigDecimal riskControl = BigDecimal.ONE.subtract(brokenLimitRisk.multiply(new BigDecimal("0.6500"))
                .add(highPositionFeedback.multiply(new BigDecimal("0.3500"))).min(ONE));
        return refillQuality.multiply(new BigDecimal("0.3000"))
                .add(rearFeedback.multiply(new BigDecimal("0.2500")))
                .add(shrinkAcceleration.multiply(new BigDecimal("0.1700")))
                .add(limitStrength.multiply(new BigDecimal("0.1300")))
                .add(riskControl.multiply(new BigDecimal("0.1500")))
                .max(ZERO)
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> filterByScope(List<Map<String, Object>> rows, Set<String> scopedStocks) {
        if (scopedStocks == null || scopedStocks.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> scopedStocks.contains(text(row.get("stock_code"), "")))
                .toList();
    }

    private List<Map<String, Object>> filterHighPositionRows(List<Map<String, Object>> rows, String plateCode) {
        if (!StringUtils.hasText(plateCode)) {
            return rows;
        }
        return rows.stream()
                .filter(row -> relatedPlates(row).contains(plateCode))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> relatedPlates(Map<String, Object> row) {
        Object value = row.get("related_plate_codes");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        String text = Objects.toString(value, "").trim();
        if (text.isEmpty()) {
            return List.of();
        }
        return List.of(text.replace("[", "").replace("]", "").replace("\"", "").split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private BigDecimal averageSealScore(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = rows.stream()
                .map(row -> cap(defaultZero(rowDecimal(row, "seal_amount")).divide(new BigDecimal("1000000000"), 4, RoundingMode.HALF_UP)))
                .reduce(ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal refillSpeedScore(List<Map<String, Object>> eventRows) {
        Map<String, LocalDateTime> lastOpenByStock = new java.util.HashMap<>();
        List<BigDecimal> speedScores = new ArrayList<>();
        for (Map<String, Object> row : eventRows.stream()
                .sorted(Comparator.comparing(row -> dateTime(row.get("event_time")), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            String stockCode = text(row.get("stock_code"), "");
            String eventType = normalize(row.get("event_type"));
            LocalDateTime eventTime = dateTime(row.get("event_time"));
            if (!StringUtils.hasText(stockCode) || eventTime == null) {
                continue;
            }
            if ("OPEN_LIMIT".equals(eventType) || "BROKEN_LIMIT".equals(eventType)) {
                lastOpenByStock.put(stockCode, eventTime);
            }
            if ("REFILL_LIMIT".equals(eventType) || "SEAL_LIMIT".equals(eventType)) {
                LocalDateTime openTime = lastOpenByStock.get(stockCode);
                if (openTime != null && !eventTime.isBefore(openTime)) {
                    long minutes = java.time.Duration.between(openTime, eventTime).toMinutes();
                    speedScores.add(BigDecimal.ONE.subtract(cap(BigDecimal.valueOf(minutes).divide(new BigDecimal("120"), 4, RoundingMode.HALF_UP))));
                }
            }
        }
        if (speedScores.isEmpty()) {
            return ZERO;
        }
        return speedScores.stream().reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(speedScores.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal dailyRefillProxy(List<Map<String, Object>> limitRows) {
        if (limitRows.isEmpty()) {
            return ZERO;
        }
        long repaired = limitRows.stream()
                .filter(row -> isLimitUp(row) && defaultZero(rowDecimal(row, "open_limit_times")).compareTo(BigDecimal.ZERO) > 0)
                .count();
        long opened = limitRows.stream()
                .filter(row -> defaultZero(rowDecimal(row, "open_limit_times")).compareTo(BigDecimal.ZERO) > 0 || isBroken(row))
                .count();
        if (opened <= 0) {
            return ZERO;
        }
        return cap(BigDecimal.valueOf(repaired).divide(BigDecimal.valueOf(opened), 4, RoundingMode.HALF_UP));
    }

    private BigDecimal average(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = rows.stream()
                .map(row -> defaultZero(rowDecimal(row, key)))
                .reduce(ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal bandScore(BigDecimal value, BigDecimal low, BigDecimal ideal, BigDecimal high) {
        if (value.compareTo(low) <= 0) {
            return cap(value.divide(low.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("0.5000")));
        }
        if (value.compareTo(ideal) <= 0) {
            return new BigDecimal("0.5000").add(value.subtract(low).divide(ideal.subtract(low), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("0.5000")));
        }
        if (value.compareTo(high) >= 0) {
            return new BigDecimal("0.3000");
        }
        return BigDecimal.ONE.subtract(value.subtract(ideal).divide(high.subtract(ideal), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("0.7000")));
    }

    private long countStatus(List<Map<String, Object>> rows, String status) {
        return rows.stream().filter(row -> status.equals(normalize(row.get("limit_status")))).count();
    }

    private long countEvent(List<Map<String, Object>> rows, String eventType) {
        return rows.stream().filter(row -> eventType.equals(normalize(row.get("event_type")))).count();
    }

    private boolean isLimitUp(Map<String, Object> row) {
        return "LIMIT_UP".equals(normalize(row.get("limit_status")));
    }

    private boolean isBroken(Map<String, Object> row) {
        String status = normalize(row.get("limit_status"));
        return "BROKEN_LIMIT".equals(status) || "OPEN_LIMIT".equals(status);
    }

    private boolean isSevereFeedback(String feedbackType) {
        return "BIG_DROP".equals(feedbackType) || "LIMIT_DOWN".equals(feedbackType)
                || "HIGH_VOLUME_FAIL".equals(feedbackType) || "BROKEN_LIMIT".equals(feedbackType);
    }

    private List<Map<String, Object>> safeRows(FactTable table, FactorCalculateRequest request) {
        List<Map<String, Object>> rows = marketFactService.findByTradeDate(table, request.getTradeDate());
        return rows == null ? List.of() : rows;
    }

    private BigDecimal normalizeScore(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return cap(value.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        }
        return cap(value);
    }

    private BigDecimal cap(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.max(ZERO).min(ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private BigDecimal rowDecimal(Map<String, Object> row, String key) {
        if (row == null || row.get(key) == null) {
            return null;
        }
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = Objects.toString(value, "").trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = Objects.toString(value, "").trim();
        if (text.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text.replace(" ", "T"));
    }

    private String normalize(Object value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? fallback : text;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String stringParam(FactorCalculateRequest request, String key, String defaultValue) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(request.getParams().get(key));
    }

    private record PatternFacts(
            String targetCode,
            String targetName,
            Set<String> scopedStocks,
            Map<String, Object> plateRow,
            List<Map<String, Object>> limitRows,
            List<Map<String, Object>> eventRows,
            List<Map<String, Object>> klineRows,
            List<Map<String, Object>> highPositionRows
    ) {
    }
}
