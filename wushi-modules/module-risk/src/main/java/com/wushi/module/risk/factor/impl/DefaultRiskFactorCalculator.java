package com.wushi.module.risk.factor.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.FactorResult;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.risk.factor.RiskFactorCalculator;
import com.wushi.module.rule.factor.AbstractFactorCalculator;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.factor.support.FactorEvidenceConverter;
import com.wushi.module.rule.factor.support.ThresholdMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class DefaultRiskFactorCalculator extends AbstractFactorCalculator implements RiskFactorCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
    private static final String CALCULATOR_CODE = "RISK_FACTOR_CALCULATOR";
    private static final List<String> FACTOR_CODES = List.of(
            "RISK_HIGH_POSITION_FEEDBACK",
            "RISK_BROKEN_LIMIT_RATE",
            "RISK_LOSS_SPREAD",
            "RISK_LEADER_FAIL",
            "RISK_PLATE_LOSS"
    );

    private final MarketFactService marketFactService;

    public DefaultRiskFactorCalculator(ThresholdMatcher thresholdMatcher,
                                       FactorEvidenceConverter evidenceConverter,
                                       MarketFactService marketFactService) {
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
        if (request.getEngineType() != null && request.getEngineType() != EngineType.RISK) {
            throw new IllegalArgumentException("Risk factor calculator only supports RISK engine");
        }
        String plateCode = StringUtils.hasText(request.getTargetCode()) && !"MARKET".equalsIgnoreCase(request.getTargetCode())
                ? request.getTargetCode()
                : stringParam(request, "plateCode", null);
        RiskFacts facts = resolveFacts(request, plateCode);
        String sourceKey = "tradeDate=" + request.getTradeDate() + ",target=" + (StringUtils.hasText(plateCode) ? plateCode : "MARKET");

        BigDecimal highFeedback = highPositionFeedback(facts);
        BigDecimal brokenRate = brokenLimitRate(facts);
        BigDecimal lossSpread = lossSpread(facts);
        BigDecimal leaderFail = leaderFail(facts);
        BigDecimal plateLoss = plateLoss(facts);

        return assemble(CALCULATOR_CODE, request.getRuleContext(), List.of(
                buildFactor(request, "RISK_HIGH_POSITION_FEEDBACK", "高位负反馈", highFeedback, "high_position_feedback_daily", sourceKey,
                        "统计高位跌停、大阴、断板后无修复和高位炸板失败，衡量退潮压力。"),
                buildFactor(request, "RISK_BROKEN_LIMIT_RATE", "炸板率", brokenRate, "stock_limit_status_daily", sourceKey,
                        "用炸板/开板失败占冲板样本比例判断短线承接是否脆弱。"),
                buildFactor(request, "RISK_LOSS_SPREAD", "亏钱效应扩散", lossSpread, "market_breadth_daily_snapshot", sourceKey,
                        "由市场亏钱效应、跌停扩散和下跌家数占比衡量亏钱效应是否外溢。"),
                buildFactor(request, "RISK_LEADER_FAIL", "龙头失败", leaderFail, "stock_limit_status_daily", sourceKey,
                        "高位核心断板、炸板或跌停且缺少修复时，视为龙头失败风险。"),
                buildFactor(request, "RISK_PLATE_LOSS", "板块失速", plateLoss, "plate_daily_snapshot", sourceKey,
                        "板块涨停减少、炸板/跌停增加、资金流出和中位数走弱形成板块失速风险。")
        ));
    }

    private RiskFacts resolveFacts(FactorCalculateRequest request, String plateCode) {
        List<Map<String, Object>> limitRows = safeRows(FactTable.STOCK_LIMIT_STATUS_DAILY, request);
        List<Map<String, Object>> highRows = safeRows(FactTable.HIGH_POSITION_FEEDBACK_DAILY, request);
        List<Map<String, Object>> breadthRows = safeRows(FactTable.MARKET_BREADTH_DAILY_SNAPSHOT, request);
        List<Map<String, Object>> plateRows = safeRows(FactTable.PLATE_DAILY_SNAPSHOT, request);
        if (StringUtils.hasText(plateCode)) {
            List<String> scopedStocks = safeRows(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, request).stream()
                    .filter(row -> plateCode.equals(text(row.get("plate_code"), "")))
                    .map(row -> text(row.get("stock_code"), ""))
                    .filter(StringUtils::hasText)
                    .toList();
            limitRows = scopedStocks.isEmpty() ? List.of() : limitRows.stream()
                    .filter(row -> scopedStocks.contains(text(row.get("stock_code"), ""))).toList();
            highRows = highRows.stream().filter(row -> relatedPlates(row).contains(plateCode)).toList();
            plateRows = plateRows.stream().filter(row -> plateCode.equals(text(row.get("plate_code"), ""))).toList();
        }
        return new RiskFacts(limitRows, highRows, breadthRows.isEmpty() ? Map.of() : breadthRows.get(0),
                plateRows.isEmpty() ? Map.of() : plateRows.get(0));
    }

    private BigDecimal highPositionFeedback(RiskFacts facts) {
        if (facts.highRows().isEmpty()) {
            return ZERO;
        }
        BigDecimal impact = average(facts.highRows(), "impact_score");
        BigDecimal severe = BigDecimal.valueOf(facts.highRows().stream().filter(row -> isSevere(normalize(row.get("feedback_type")))).count())
                .divide(BigDecimal.valueOf(facts.highRows().size()), 4, RoundingMode.HALF_UP);
        BigDecimal count = cap(BigDecimal.valueOf(facts.highRows().size()).divide(new BigDecimal("8"), 4, RoundingMode.HALF_UP));
        return normalizeScore(impact).multiply(new BigDecimal("0.5000"))
                .add(severe.multiply(new BigDecimal("0.3000")))
                .add(count.multiply(new BigDecimal("0.2000")))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal brokenLimitRate(RiskFacts facts) {
        BigDecimal broken = BigDecimal.valueOf(countStatus(facts.limitRows(), "BROKEN_LIMIT") + countStatus(facts.limitRows(), "OPEN_LIMIT"));
        BigDecimal limitUp = BigDecimal.valueOf(countStatus(facts.limitRows(), "LIMIT_UP"));
        BigDecimal touch = broken.add(limitUp);
        if (touch.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return cap(broken.divide(touch, 4, RoundingMode.HALF_UP));
    }

    private BigDecimal lossSpread(RiskFacts facts) {
        Map<String, Object> row = facts.breadthRow();
        if (row.isEmpty()) {
            return ZERO;
        }
        BigDecimal loss = normalizeScore(rowDecimal(row, "loss_effect_score"));
        BigDecimal down = defaultZero(rowDecimal(row, "down_count"));
        BigDecimal up = defaultZero(rowDecimal(row, "up_count"));
        BigDecimal limitDown = defaultZero(rowDecimal(row, "limit_down_count"));
        BigDecimal breadthRisk = down.add(up).compareTo(BigDecimal.ZERO) <= 0 ? ZERO : cap(down.divide(down.add(up), 4, RoundingMode.HALF_UP));
        BigDecimal downRisk = cap(limitDown.divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP));
        return loss.multiply(new BigDecimal("0.5000")).add(breadthRisk.multiply(new BigDecimal("0.3000")))
                .add(downRisk.multiply(new BigDecimal("0.2000"))).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal leaderFail(RiskFacts facts) {
        long failed = facts.limitRows().stream()
                .filter(row -> defaultZero(rowDecimal(row, "consecutive_limit_up_days")).compareTo(new BigDecimal("3")) >= 0)
                .filter(row -> isBroken(row) || "LIMIT_DOWN".equals(normalize(row.get("limit_status"))))
                .count();
        return failed > 0 ? ONE : ZERO;
    }

    private BigDecimal plateLoss(RiskFacts facts) {
        Map<String, Object> row = facts.plateRow();
        if (row.isEmpty()) {
            return ZERO;
        }
        BigDecimal broken = defaultZero(rowDecimal(row, "broken_limit_count"));
        BigDecimal limitDown = defaultZero(rowDecimal(row, "limit_down_count"));
        BigDecimal limitUp = defaultZero(rowDecimal(row, "limit_up_count"));
        BigDecimal median = defaultZero(rowDecimal(row, "median_change_pct"));
        BigDecimal flow = defaultZero(rowDecimal(row, "main_net_inflow"));
        BigDecimal structureRisk = cap(broken.add(limitDown).divide(limitUp.add(broken).add(limitDown).max(BigDecimal.ONE), 4, RoundingMode.HALF_UP));
        BigDecimal medianRisk = cap(median.negate().max(ZERO).divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP));
        BigDecimal flowRisk = flow.compareTo(BigDecimal.ZERO) < 0 ? new BigDecimal("0.3000") : ZERO;
        return structureRisk.multiply(new BigDecimal("0.5000")).add(medianRisk.multiply(new BigDecimal("0.3000")))
                .add(flowRisk.multiply(new BigDecimal("0.2000"))).min(ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private long countStatus(List<Map<String, Object>> rows, String status) {
        return rows.stream().filter(row -> status.equals(normalize(row.get("limit_status")))).count();
    }

    private boolean isBroken(Map<String, Object> row) {
        String status = normalize(row.get("limit_status"));
        return "BROKEN_LIMIT".equals(status) || "OPEN_LIMIT".equals(status);
    }

    private boolean isSevere(String type) {
        return "BIG_DROP".equals(type) || "LIMIT_DOWN".equals(type) || "HIGH_VOLUME_FAIL".equals(type) || "BROKEN_LIMIT".equals(type);
    }

    @SuppressWarnings("unchecked")
    private List<String> relatedPlates(Map<String, Object> row) {
        Object value = row.get("related_plate_codes");
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        String text = Objects.toString(value, "").replace("[", "").replace("]", "").replace("\"", "").trim();
        return text.isEmpty() ? List.of() : List.of(text.split(",")).stream().map(String::trim).filter(StringUtils::hasText).toList();
    }

    private BigDecimal average(List<Map<String, Object>> rows, String key) {
        if (rows.isEmpty()) {
            return ZERO;
        }
        return rows.stream().map(row -> defaultZero(rowDecimal(row, key))).reduce(ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rows.size()), 4, RoundingMode.HALF_UP);
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
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    private String normalize(Object value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value, String fallback) {
        String text = Objects.toString(value, "").trim();
        return text.isEmpty() ? fallback : text;
    }

    private String stringParam(FactorCalculateRequest request, String key, String defaultValue) {
        if (request.getParams() == null || request.getParams().get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(request.getParams().get(key));
    }

    private record RiskFacts(
            List<Map<String, Object>> limitRows,
            List<Map<String, Object>> highRows,
            Map<String, Object> breadthRow,
            Map<String, Object> plateRow
    ) {
    }
}
