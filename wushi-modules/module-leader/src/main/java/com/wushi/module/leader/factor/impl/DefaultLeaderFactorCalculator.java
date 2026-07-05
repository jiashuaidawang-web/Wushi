package com.wushi.module.leader.factor.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.FactorResult;
import com.wushi.module.leader.factor.LeaderFactorCalculator;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.rule.factor.AbstractFactorCalculator;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.factor.support.FactorEvidenceConverter;
import com.wushi.module.rule.factor.support.ThresholdMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class DefaultLeaderFactorCalculator extends AbstractFactorCalculator implements LeaderFactorCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final String CALCULATOR_CODE = "LEADER_FACTOR_CALCULATOR";
    private static final String SOURCE_LIMIT = "stock_limit_status_daily";
    private static final String SOURCE_KLINE = "stock_daily_kline";
    private static final String SOURCE_EVENT = "stock_limit_intraday_event";
    private static final String SOURCE_PLATE = "plate_daily_snapshot";
    private static final String SOURCE_RELATION = "stock_plate_relation_snapshot";
    private static final List<String> FACTOR_CODES = List.of(
            "LEADER_POSITION_SCORE",
            "LEADER_CONSECUTIVE_LIMIT_DAYS",
            "LEADER_DRIVE_SCORE",
            "LEADER_POPULARITY_SCORE",
            "LEADER_DIVERGENCE_REPAIR",
            "LEADER_CHALLENGE_RISK"
    );

    private final MarketFactService marketFactService;

    public DefaultLeaderFactorCalculator(
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
        if (request.getEngineType() != null && request.getEngineType() != EngineType.LEADER) {
            throw new IllegalArgumentException("Leader factor calculator only supports LEADER engine");
        }

        CandidateFacts facts = resolveCandidateFacts(request);
        String stockCode = text(facts.limitRow().get("stock_code"), request.getTargetCode());
        String stockName = text(facts.limitRow().get("stock_name"), request.getTargetName());
        String plateCode = text(facts.relationRow().get("plate_code"), stringParam(request, "plateCode", ""));
        String plateName = text(facts.relationRow().get("plate_name"), stringParam(request, "plateName", ""));
        String sourceKey = "tradeDate=" + request.getTradeDate() + ",stockCode=" + defaultText(stockCode, "AUTO");

        BigDecimal consecutiveLimitDays = rowDecimal(facts.limitRow(), "consecutive_limit_up_days");
        BigDecimal divergenceRepair = resolveDivergenceRepair(facts);
        BigDecimal popularity = resolvePopularity(facts);
        BigDecimal drive = resolveDrive(facts, plateCode);
        BigDecimal challengeRisk = resolveChallengeRisk(request, stockCode, plateCode, consecutiveLimitDays, drive);
        BigDecimal position = resolvePositionScore(consecutiveLimitDays, popularity, drive, divergenceRepair, challengeRisk, facts);

        List<FactorResult> factors = new ArrayList<>();
        factors.add(buildFactor(request, "LEADER_POSITION_SCORE", "龙头地位评分", position, SOURCE_LIMIT, sourceKey,
                "综合空间高度、人气、带动性、分歧修复和被挑战风险，表达该股是否正在竞争龙头地位。"));
        factors.add(buildFactor(request, "LEADER_CONSECUTIVE_LIMIT_DAYS", "连板高度", consecutiveLimitDays, SOURCE_LIMIT, sourceKey,
                "连续涨停天数是短线情绪空间锚，代表市场愿意给该股接力的高度。"));
        factors.add(buildFactor(request, "LEADER_DRIVE_SCORE", "带动性", drive, SOURCE_PLATE, sourceKey,
                "带动性由所属板块涨停家数、梯队完整度、领涨股匹配关系和板块上涨宽度估算。"));
        factors.add(buildFactor(request, "LEADER_POPULARITY_SCORE", "人气强度", popularity, SOURCE_KLINE, sourceKey,
                "人气强度由成交额、换手、涨幅、封单金额和连板高度合成，衡量资金关注度和接力热度。"));
        factors.add(buildFactor(request, "LEADER_DIVERGENCE_REPAIR", "分歧修复", divergenceRepair, SOURCE_EVENT, sourceKey,
                "分歧修复优先读取盘中开板/回封事件；缺失事件时使用开板次数、封单和涨停状态做日线代理。"));
        factors.add(buildFactor(request, "LEADER_CHALLENGE_RISK", "被挑战风险", challengeRisk, SOURCE_RELATION, sourceKey,
                "被挑战风险衡量同板块是否存在高度、带动性或人气接近甚至超过当前候选的竞争者。"));

        return assemble(CALCULATOR_CODE, request.getRuleContext(), factors);
    }

    private CandidateFacts resolveCandidateFacts(FactorCalculateRequest request) {
        List<Map<String, Object>> limitRows = safeRows(FactTable.STOCK_LIMIT_STATUS_DAILY, request);
        Map<String, Object> limitRow = resolveLimitRow(request, limitRows);
        String stockCode = text(limitRow.get("stock_code"), request.getTargetCode());
        Map<String, Object> klineRow = findByStock(FactTable.STOCK_DAILY_KLINE, request, stockCode);
        Map<String, Object> relationRow = findRelation(request, stockCode);
        Map<String, Object> plateRow = findPlate(request, relationRow, stringParam(request, "plateCode", ""));
        List<Map<String, Object>> eventRows = findEvents(request, stockCode);
        return new CandidateFacts(limitRow, klineRow, relationRow, plateRow, eventRows);
    }

    private Map<String, Object> resolveLimitRow(FactorCalculateRequest request, List<Map<String, Object>> limitRows) {
        if (limitRows.isEmpty()) {
            return Map.of();
        }
        if (StringUtils.hasText(request.getTargetCode())) {
            return limitRows.stream()
                    .filter(row -> request.getTargetCode().equals(text(row.get("stock_code"), null)))
                    .findFirst()
                    .orElse(Map.of());
        }
        return limitRows.stream()
                .filter(row -> isLimitUp(row) || isBrokenLimit(row))
                .max(Comparator.comparing(row -> candidateScore(row, findByStock(FactTable.STOCK_DAILY_KLINE, request, text(row.get("stock_code"), "")))))
                .orElse(Map.of());
    }

    private BigDecimal candidateScore(Map<String, Object> limitRow, Map<String, Object> klineRow) {
        BigDecimal height = cap(defaultZero(rowDecimal(limitRow, "consecutive_limit_up_days")).divide(new BigDecimal("6"), 4, RoundingMode.HALF_UP));
        BigDecimal seal = cap(defaultZero(rowDecimal(limitRow, "seal_amount")).divide(new BigDecimal("1000000000"), 4, RoundingMode.HALF_UP));
        BigDecimal amount = cap(defaultZero(rowDecimal(klineRow, "amount")).divide(new BigDecimal("10000000000"), 4, RoundingMode.HALF_UP));
        BigDecimal turnover = cap(defaultZero(rowDecimal(klineRow, "turnover_rate")).divide(new BigDecimal("20"), 4, RoundingMode.HALF_UP));
        BigDecimal status = isLimitUp(limitRow) ? BigDecimal.ONE : new BigDecimal("0.5000");
        return height.multiply(new BigDecimal("45"))
                .add(status.multiply(new BigDecimal("20")))
                .add(seal.multiply(new BigDecimal("15")))
                .add(amount.multiply(new BigDecimal("10")))
                .add(turnover.multiply(new BigDecimal("10")));
    }

    private BigDecimal resolvePositionScore(BigDecimal consecutiveLimitDays, BigDecimal popularity, BigDecimal drive,
                                            BigDecimal divergenceRepair, BigDecimal challengeRisk, CandidateFacts facts) {
        BigDecimal heightScore = cap(defaultZero(consecutiveLimitDays).divide(new BigDecimal("6"), 4, RoundingMode.HALF_UP));
        BigDecimal statusScore = isLimitUp(facts.limitRow()) ? BigDecimal.ONE : isBrokenLimit(facts.limitRow()) ? new BigDecimal("0.3000") : ZERO;
        BigDecimal leaderMatch = isPlateLeader(facts) ? BigDecimal.ONE : ZERO;
        BigDecimal base = heightScore.multiply(new BigDecimal("0.3200"))
                .add(defaultZero(popularity).multiply(new BigDecimal("0.1800")))
                .add(defaultZero(drive).multiply(new BigDecimal("0.2000")))
                .add(defaultZero(divergenceRepair).multiply(new BigDecimal("0.1700")))
                .add(statusScore.multiply(new BigDecimal("0.0800")))
                .add(leaderMatch.multiply(new BigDecimal("0.0500")))
                .subtract(defaultZero(challengeRisk).multiply(new BigDecimal("0.1500")));
        return cap(base);
    }

    private BigDecimal resolvePopularity(CandidateFacts facts) {
        BigDecimal amountScore = cap(defaultZero(rowDecimal(facts.klineRow(), "amount")).divide(new BigDecimal("10000000000"), 4, RoundingMode.HALF_UP));
        BigDecimal turnoverScore = cap(defaultZero(rowDecimal(facts.klineRow(), "turnover_rate")).divide(new BigDecimal("20"), 4, RoundingMode.HALF_UP));
        BigDecimal changeScore = cap(defaultZero(rowDecimal(facts.klineRow(), "change_pct")).max(BigDecimal.ZERO).divide(new BigDecimal("10"), 4, RoundingMode.HALF_UP));
        BigDecimal sealScore = cap(defaultZero(rowDecimal(facts.limitRow(), "seal_amount")).divide(new BigDecimal("1000000000"), 4, RoundingMode.HALF_UP));
        BigDecimal heightScore = cap(defaultZero(rowDecimal(facts.limitRow(), "consecutive_limit_up_days")).divide(new BigDecimal("6"), 4, RoundingMode.HALF_UP));
        return amountScore.multiply(new BigDecimal("0.3000"))
                .add(turnoverScore.multiply(new BigDecimal("0.2500")))
                .add(changeScore.multiply(new BigDecimal("0.1800")))
                .add(sealScore.multiply(new BigDecimal("0.1700")))
                .add(heightScore.multiply(new BigDecimal("0.1000")))
                .min(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDrive(CandidateFacts facts, String plateCode) {
        BigDecimal limitUpScore = cap(defaultZero(rowDecimal(facts.plateRow(), "limit_up_count")).divide(new BigDecimal("20"), 4, RoundingMode.HALF_UP));
        BigDecimal ladderScore = normalizeScore(rowDecimal(facts.plateRow(), "ladder_integrity_score"));
        BigDecimal breadthScore = plateBreadthScore(facts.plateRow());
        BigDecimal leaderMatch = isPlateLeader(facts) ? BigDecimal.ONE : ZERO;
        BigDecimal relationPenalty = relationQualityPenalty(facts.relationRow(), plateCode);
        return limitUpScore.multiply(new BigDecimal("0.3500"))
                .add(ladderScore.multiply(new BigDecimal("0.3000")))
                .add(breadthScore.multiply(new BigDecimal("0.2000")))
                .add(leaderMatch.multiply(new BigDecimal("0.1500")))
                .subtract(relationPenalty)
                .max(ZERO)
                .min(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveDivergenceRepair(CandidateFacts facts) {
        long openCount = facts.eventRows().stream().filter(row -> "OPEN_LIMIT".equals(normalize(row.get("event_type")))).count();
        long refillCount = facts.eventRows().stream().filter(row -> "REFILL_LIMIT".equals(normalize(row.get("event_type")))
                || "SEAL_LIMIT".equals(normalize(row.get("event_type")))).count();
        if (!facts.eventRows().isEmpty()) {
            if (openCount == 0 && refillCount > 0 && isLimitUp(facts.limitRow())) {
                return new BigDecimal("0.8500");
            }
            if (openCount > 0 && refillCount >= openCount && isLimitUp(facts.limitRow())) {
                return new BigDecimal("0.7500");
            }
            if (openCount > 0 && isBrokenLimit(facts.limitRow())) {
                return new BigDecimal("0.2500");
            }
        }
        BigDecimal openLimitTimes = defaultZero(rowDecimal(facts.limitRow(), "open_limit_times"));
        BigDecimal sealScore = cap(defaultZero(rowDecimal(facts.limitRow(), "seal_amount")).divide(new BigDecimal("800000000"), 4, RoundingMode.HALF_UP));
        if (isLimitUp(facts.limitRow()) && openLimitTimes.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal("0.6500").add(sealScore.multiply(new BigDecimal("0.2000"))).min(BigDecimal.ONE);
        }
        if (isLimitUp(facts.limitRow())) {
            BigDecimal openPenalty = cap(openLimitTimes.divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP)).multiply(new BigDecimal("0.3500"));
            return new BigDecimal("0.6500").add(sealScore.multiply(new BigDecimal("0.2000"))).subtract(openPenalty).max(ZERO);
        }
        return isBrokenLimit(facts.limitRow()) ? new BigDecimal("0.2000") : ZERO;
    }

    private BigDecimal resolveChallengeRisk(FactorCalculateRequest request, String stockCode, String plateCode,
                                            BigDecimal consecutiveLimitDays, BigDecimal driveScore) {
        if (!StringUtils.hasText(stockCode) || !StringUtils.hasText(plateCode)) {
            return new BigDecimal("0.5000");
        }
        BigDecimal currentHeight = defaultZero(consecutiveLimitDays);
        BigDecimal strongestChallenge = ZERO;
        List<Map<String, Object>> relations = safeRows(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, request);
        for (Map<String, Object> relation : relations) {
            if (stockCode.equals(text(relation.get("stock_code"), "")) || !plateCode.equals(text(relation.get("plate_code"), ""))) {
                continue;
            }
            Map<String, Object> peerLimit = findByStock(FactTable.STOCK_LIMIT_STATUS_DAILY, request, text(relation.get("stock_code"), ""));
            if (peerLimit.isEmpty()) {
                continue;
            }
            BigDecimal peerHeight = defaultZero(rowDecimal(peerLimit, "consecutive_limit_up_days"));
            BigDecimal heightPressure = peerHeight.compareTo(currentHeight) >= 0
                    ? new BigDecimal("0.4500")
                    : cap(peerHeight.divide(currentHeight.max(BigDecimal.ONE), 4, RoundingMode.HALF_UP)).multiply(new BigDecimal("0.3000"));
            BigDecimal peerStatus = isLimitUp(peerLimit) ? new BigDecimal("0.2500") : ZERO;
            BigDecimal peerSeal = cap(defaultZero(rowDecimal(peerLimit, "seal_amount")).divide(new BigDecimal("1000000000"), 4, RoundingMode.HALF_UP))
                    .multiply(new BigDecimal("0.2000"));
            BigDecimal driveGap = BigDecimal.ONE.subtract(defaultZero(driveScore)).max(ZERO).multiply(new BigDecimal("0.1000"));
            strongestChallenge = strongestChallenge.max(heightPressure.add(peerStatus).add(peerSeal).add(driveGap));
        }
        return cap(strongestChallenge);
    }

    private Map<String, Object> findByStock(FactTable table, FactorCalculateRequest request, String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            return Map.of();
        }
        return safeRows(table, request).stream()
                .filter(row -> stockCode.equals(text(row.get("stock_code"), "")))
                .findFirst()
                .orElse(Map.of());
    }

    private Map<String, Object> findRelation(FactorCalculateRequest request, String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            return Map.of();
        }
        String requestedPlateCode = stringParam(request, "plateCode", "");
        return safeRows(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, request).stream()
                .filter(row -> stockCode.equals(text(row.get("stock_code"), "")))
                .filter(row -> !StringUtils.hasText(requestedPlateCode) || requestedPlateCode.equals(text(row.get("plate_code"), "")))
                .max(Comparator.comparing(row -> defaultZero(rowDecimal(row, "relation_confidence"))))
                .orElse(Map.of());
    }

    private Map<String, Object> findPlate(FactorCalculateRequest request, Map<String, Object> relationRow, String fallbackPlateCode) {
        String plateCode = text(relationRow.get("plate_code"), fallbackPlateCode);
        if (!StringUtils.hasText(plateCode)) {
            return Map.of();
        }
        return safeRows(FactTable.PLATE_DAILY_SNAPSHOT, request).stream()
                .filter(row -> plateCode.equals(text(row.get("plate_code"), "")))
                .findFirst()
                .orElse(Map.of());
    }

    private List<Map<String, Object>> findEvents(FactorCalculateRequest request, String stockCode) {
        if (!StringUtils.hasText(stockCode)) {
            return List.of();
        }
        return safeRows(FactTable.STOCK_LIMIT_INTRADAY_EVENT, request).stream()
                .filter(row -> stockCode.equals(text(row.get("stock_code"), "")))
                .toList();
    }

    private List<Map<String, Object>> safeRows(FactTable table, FactorCalculateRequest request) {
        List<Map<String, Object>> rows = marketFactService.findByTradeDate(table, request.getTradeDate());
        return rows == null ? List.of() : rows;
    }

    private boolean isPlateLeader(CandidateFacts facts) {
        String stockCode = text(facts.limitRow().get("stock_code"), "");
        String leaderStockCode = text(facts.plateRow().get("leader_stock_code"), "");
        return StringUtils.hasText(stockCode) && stockCode.equals(leaderStockCode);
    }

    private boolean isLimitUp(Map<String, Object> row) {
        return "LIMIT_UP".equals(normalize(row.get("limit_status")));
    }

    private boolean isBrokenLimit(Map<String, Object> row) {
        String status = normalize(row.get("limit_status"));
        return "BROKEN_LIMIT".equals(status) || "OPEN_LIMIT".equals(status);
    }

    private BigDecimal plateBreadthScore(Map<String, Object> plateRow) {
        BigDecimal up = defaultZero(rowDecimal(plateRow, "up_count"));
        BigDecimal down = defaultZero(rowDecimal(plateRow, "down_count"));
        BigDecimal total = up.add(down);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return cap(up.divide(total, 4, RoundingMode.HALF_UP));
    }

    private BigDecimal relationQualityPenalty(Map<String, Object> relationRow, String plateCode) {
        if (!StringUtils.hasText(plateCode)) {
            return new BigDecimal("0.0800");
        }
        String quality = normalize(relationRow.get("relation_quality_level"));
        if ("LOW".equals(quality)) {
            return new BigDecimal("0.0800");
        }
        boolean currentBackfill = "1".equals(text(relationRow.get("is_current_backfill"), ""));
        return currentBackfill ? new BigDecimal("0.0500") : ZERO;
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
        return value.max(ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
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

    private String normalize(Object value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? fallback : text;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String stringParam(FactorCalculateRequest request, String key, String defaultValue) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(request.getParams().get(key));
    }

    private record CandidateFacts(
            Map<String, Object> limitRow,
            Map<String, Object> klineRow,
            Map<String, Object> relationRow,
            Map<String, Object> plateRow,
            List<Map<String, Object>> eventRows
    ) {
    }
}
