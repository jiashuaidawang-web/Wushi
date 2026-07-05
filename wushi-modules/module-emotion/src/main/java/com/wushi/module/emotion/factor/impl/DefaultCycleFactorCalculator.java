package com.wushi.module.emotion.factor.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.FactorResult;
import com.wushi.module.emotion.factor.CycleFactorCalculator;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.rule.factor.AbstractFactorCalculator;
import com.wushi.module.rule.factor.FactorCalculateRequest;
import com.wushi.module.rule.factor.FactorCalculateResult;
import com.wushi.module.rule.factor.support.FactorEvidenceConverter;
import com.wushi.module.rule.factor.support.ThresholdMatcher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class DefaultCycleFactorCalculator extends AbstractFactorCalculator implements CycleFactorCalculator {

    private static final String CALCULATOR_CODE = "CYCLE_FACTOR_CALCULATOR";
    private static final String SOURCE_BREADTH = "market_breadth_daily_snapshot";
    private static final String SOURCE_LIMIT_STATUS = "stock_limit_status_daily";
    private static final List<String> FACTOR_CODES = List.of(
            "CYCLE_LIMIT_UP_COUNT",
            "CYCLE_LIMIT_DOWN_COUNT",
            "CYCLE_BROKEN_LIMIT_COUNT",
            "CYCLE_MONEY_EFFECT_SCORE",
            "CYCLE_LOSS_EFFECT_SCORE",
            "CYCLE_ABOVE_MA20_RATIO"
    );

    private final MarketFactService marketFactService;

    public DefaultCycleFactorCalculator(
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
        if (request.getEngineType() != null && request.getEngineType() != EngineType.CYCLE) {
            throw new IllegalArgumentException("Cycle factor calculator only supports CYCLE engine");
        }

        Map<String, Object> breadth = firstRow(FactTable.MARKET_BREADTH_DAILY_SNAPSHOT, request);
        List<Map<String, Object>> limitRows = marketFactService.findByTradeDate(FactTable.STOCK_LIMIT_STATUS_DAILY, request.getTradeDate());

        BigDecimal limitUpCount = firstNonNull(decimal(breadth, "limit_up_count"), countLimitStatus(limitRows, LimitKind.UP));
        BigDecimal limitDownCount = firstNonNull(decimal(breadth, "limit_down_count"), countLimitStatus(limitRows, LimitKind.DOWN));
        BigDecimal brokenLimitCount = firstNonNull(decimal(breadth, "broken_limit_count"), countLimitStatus(limitRows, LimitKind.BROKEN));
        BigDecimal moneyEffectScore = decimal(breadth, "money_effect_score");
        BigDecimal lossEffectScore = decimal(breadth, "loss_effect_score");
        BigDecimal aboveMa20Ratio = calculateAboveMa20Ratio(breadth);

        List<FactorResult> factors = new ArrayList<>();
        String sourceKey = "tradeDate=" + request.getTradeDate();
        factors.add(buildFactor(request, "CYCLE_LIMIT_UP_COUNT", "涨停家数", limitUpCount, SOURCE_LIMIT_STATUS, sourceKey,
                "全市场涨停家数衡量短线情绪热度与赚钱效应外溢。"));
        factors.add(buildFactor(request, "CYCLE_LIMIT_DOWN_COUNT", "跌停家数", limitDownCount, SOURCE_LIMIT_STATUS, sourceKey,
                "跌停家数越低，说明亏钱效应越可控；若超过阈值，周期判断必须提高风险权重。"));
        factors.add(buildFactor(request, "CYCLE_BROKEN_LIMIT_COUNT", "炸板家数", brokenLimitCount, SOURCE_LIMIT_STATUS, sourceKey,
                "炸板家数体现分歧与承接失败，过高时说明修复或主升的一致性不足。"));
        factors.add(buildFactor(request, "CYCLE_MONEY_EFFECT_SCORE", "赚钱效应", moneyEffectScore, SOURCE_BREADTH, sourceKey,
                "赚钱效应来自涨停、上涨家数、强势延续，是判断修复和扩散的核心证据。"));
        factors.add(buildFactor(request, "CYCLE_LOSS_EFFECT_SCORE", "亏钱效应", lossEffectScore, SOURCE_BREADTH, sourceKey,
                "亏钱效应用于识别假修复、退潮杀跌和高位负反馈是否扩散。"));
        factors.add(buildFactor(request, "CYCLE_ABOVE_MA20_RATIO", "20日线以上比例", aboveMa20Ratio, SOURCE_BREADTH, sourceKey,
                "20日线以上比例衡量市场基础宽度，决定题材行情是否有足够土壤。"));

        return assemble(CALCULATOR_CODE, request.getRuleContext(), factors);
    }

    private Map<String, Object> firstRow(FactTable factTable, FactorCalculateRequest request) {
        return marketFactService.findByTradeDate(factTable, request.getTradeDate()).stream()
                .findFirst()
                .orElse(Map.of());
    }

    private BigDecimal countLimitStatus(List<Map<String, Object>> rows, LimitKind limitKind) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        long count = rows.stream()
                .filter(row -> matchesLimitKind(row, limitKind))
                .count();
        return BigDecimal.valueOf(count);
    }

    private boolean matchesLimitKind(Map<String, Object> row, LimitKind limitKind) {
        String status = normalize(row.get("limit_status"));
        Integer openLimitTimes = integer(row.get("open_limit_times"));
        return switch (limitKind) {
            case UP -> containsAny(status, "LIMIT_UP", "UP", "ZT", "涨停");
            case DOWN -> containsAny(status, "LIMIT_DOWN", "DOWN", "DT", "跌停");
            case BROKEN -> containsAny(status, "BROKEN", "OPEN", "炸", "开板") || (openLimitTimes != null && openLimitTimes > 0);
        };
    }

    private BigDecimal calculateAboveMa20Ratio(Map<String, Object> breadth) {
        BigDecimal aboveMa20Count = decimal(breadth, "above_ma20_count");
        BigDecimal upCount = decimal(breadth, "up_count");
        BigDecimal downCount = decimal(breadth, "down_count");
        BigDecimal flatCount = decimal(breadth, "flat_count");
        if (aboveMa20Count == null || upCount == null || downCount == null || flatCount == null) {
            return null;
        }
        BigDecimal total = upCount.add(downCount).add(flatCount);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return aboveMa20Count.divide(total, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private String normalize(Object value) {
        return Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String value, String... patterns) {
        for (String pattern : patterns) {
            if (value.contains(pattern.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private enum LimitKind {
        UP,
        DOWN,
        BROKEN
    }
}
