package com.wushi.module.mainline.factor.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.FactorResult;
import com.wushi.module.mainline.factor.MainlineFactorCalculator;
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
public class DefaultMainlineFactorCalculator extends AbstractFactorCalculator implements MainlineFactorCalculator {

    private static final String CALCULATOR_CODE = "MAINLINE_FACTOR_CALCULATOR";
    private static final String SOURCE_PLATE = "plate_daily_snapshot";
    private static final String SOURCE_CAPITAL = "capital_flow_daily_snapshot";
    private static final List<String> FACTOR_CODES = List.of(
            "MAINLINE_ACTIVE_DAYS",
            "MAINLINE_LIMIT_UP_COUNT",
            "MAINLINE_LADDER_INTEGRITY",
            "MAINLINE_LEADER_QUALITY",
            "MAINLINE_MIDDLE_ARMY_SUPPORT",
            "MAINLINE_REAR_RISK",
            "MAINLINE_CAPITAL_INFLOW"
    );

    private final MarketFactService marketFactService;

    public DefaultMainlineFactorCalculator(
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
        if (request.getEngineType() != null && request.getEngineType() != EngineType.MAINLINE) {
            throw new IllegalArgumentException("Mainline factor calculator only supports MAINLINE engine");
        }

        Map<String, Object> plate = resolvePlateSnapshot(request);
        String plateCode = text(plate.get("plate_code"), request.getTargetCode());
        String plateName = text(plate.get("plate_name"), request.getTargetName());
        String sourceKey = "tradeDate=" + request.getTradeDate() + ",plateCode=" + defaultText(plateCode, "AUTO");

        BigDecimal activeDays = resolveActiveDaysProxy(plate);
        BigDecimal limitUpCount = decimal(plate, "limit_up_count");
        BigDecimal ladderIntegrity = decimal(plate, "ladder_integrity_score");
        BigDecimal leaderQuality = resolveLeaderQuality(plate);
        BigDecimal middleArmySupport = resolveMiddleArmySupport(plate);
        BigDecimal rearRisk = resolveRearRisk(plate);
        BigDecimal capitalInflow = resolveCapitalInflow(request, plateCode, plate);

        List<FactorResult> factors = new ArrayList<>();
        factors.add(buildFactor(request, "MAINLINE_ACTIVE_DAYS", "连续活跃天数", activeDays, SOURCE_PLATE, sourceKey,
                "现有事实表尚未提供真实连续活跃天数，本因子优先读取 active_days；缺失时使用持续性评分换算为活跃天数代理，用于识别题材是否从脉冲走向持续。"));
        factors.add(buildFactor(request, "MAINLINE_LIMIT_UP_COUNT", "板块涨停家数", limitUpCount, SOURCE_PLATE, sourceKey,
                "板块涨停家数衡量主线情绪强度，主线不是第一天涨得多，而是能持续吸引资金接力。"));
        factors.add(buildFactor(request, "MAINLINE_LADDER_INTEGRITY", "梯队完整度", ladderIntegrity, SOURCE_PLATE, sourceKey,
                "梯队完整度衡量首板、二板、中高位是否形成结构，是主线确认的重要证据。"));
        factors.add(buildFactor(request, "MAINLINE_LEADER_QUALITY", "龙头质量", leaderQuality, SOURCE_PLATE, sourceKey,
                "当前用板块领涨股存在性、梯队完整度和涨停强度估算龙头质量；后续接入龙头竞争快照后替换为真实龙头质量分。"));
        factors.add(buildFactor(request, "MAINLINE_MIDDLE_ARMY_SUPPORT", "中军承接", middleArmySupport, SOURCE_PLATE, sourceKey,
                "中军承接用板块成交额与上涨家数比例估算，表达资金是否只做小票情绪，还是有容量承接。"));
        factors.add(buildFactor(request, "MAINLINE_REAR_RISK", "后排风险", rearRisk, SOURCE_PLATE, sourceKey,
                "后排风险由炸板、跌停相对涨停冲板结构计算，风险越低越支持主线从分歧走向确认。"));
        factors.add(buildFactor(request, "MAINLINE_CAPITAL_INFLOW", "主力净流入", capitalInflow, SOURCE_CAPITAL, sourceKey,
                "主力净流入衡量资金是否愿意持续聚焦该板块，是主线合力的重要辅助证据。"));

        return assemble(CALCULATOR_CODE, request.getRuleContext(), factors);
    }

    private Map<String, Object> resolvePlateSnapshot(FactorCalculateRequest request) {
        List<Map<String, Object>> rows = marketFactService.findByTradeDate(FactTable.PLATE_DAILY_SNAPSHOT, request.getTradeDate());
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        if (StringUtils.hasText(request.getTargetCode())) {
            return rows.stream()
                    .filter(row -> request.getTargetCode().equals(text(row.get("plate_code"), null)))
                    .findFirst()
                    .orElse(Map.of());
        }
        return rows.stream()
                .max(Comparator.comparing(this::plateCandidateScore))
                .orElse(Map.of());
    }

    private BigDecimal plateCandidateScore(Map<String, Object> row) {
        BigDecimal limitUp = defaultZero(decimal(row, "limit_up_count"));
        BigDecimal sustainability = normalizeScore(decimal(row, "sustainability_score"));
        BigDecimal ladder = normalizeScore(decimal(row, "ladder_integrity_score"));
        BigDecimal capital = defaultZero(decimal(row, "main_net_inflow")).compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
        return limitUp.add(sustainability.multiply(new BigDecimal("10")))
                .add(ladder.multiply(new BigDecimal("10")))
                .add(capital);
    }

    private BigDecimal resolveActiveDaysProxy(Map<String, Object> plate) {
        BigDecimal activeDays = decimal(plate, "active_days");
        if (activeDays != null) {
            return activeDays;
        }
        BigDecimal sustainability = decimal(plate, "sustainability_score");
        if (sustainability == null) {
            return null;
        }
        if (sustainability.compareTo(BigDecimal.ONE) <= 0) {
            return sustainability.multiply(new BigDecimal("5")).setScale(4, RoundingMode.HALF_UP);
        }
        return sustainability;
    }

    private BigDecimal resolveLeaderQuality(Map<String, Object> plate) {
        String leaderStockCode = text(plate.get("leader_stock_code"), null);
        BigDecimal ladder = normalizeScore(decimal(plate, "ladder_integrity_score"));
        BigDecimal limitUp = defaultZero(decimal(plate, "limit_up_count"));
        BigDecimal limitScore = limitUp.divide(new BigDecimal("20"), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal leaderBase = StringUtils.hasText(leaderStockCode) ? new BigDecimal("0.4000") : BigDecimal.ZERO;
        return leaderBase.add(ladder.multiply(new BigDecimal("0.4000")))
                .add(limitScore.multiply(new BigDecimal("0.2000")))
                .min(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveMiddleArmySupport(Map<String, Object> plate) {
        BigDecimal amount = defaultZero(decimal(plate, "amount"));
        BigDecimal amountScore = amount.divide(new BigDecimal("10000000000"), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        BigDecimal upCount = defaultZero(decimal(plate, "up_count"));
        BigDecimal stockCount = defaultZero(decimal(plate, "stock_count"));
        BigDecimal upRatio = stockCount.compareTo(BigDecimal.ZERO) > 0
                ? upCount.divide(stockCount, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return amountScore.multiply(new BigDecimal("0.6000"))
                .add(upRatio.multiply(new BigDecimal("0.4000")))
                .min(BigDecimal.ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveRearRisk(Map<String, Object> plate) {
        BigDecimal broken = defaultZero(decimal(plate, "broken_limit_count"));
        BigDecimal limitDown = defaultZero(decimal(plate, "limit_down_count"));
        BigDecimal limitUp = defaultZero(decimal(plate, "limit_up_count"));
        BigDecimal denominator = limitUp.add(broken).add(limitDown);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return broken.add(limitDown).divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveCapitalInflow(FactorCalculateRequest request, String plateCode, Map<String, Object> plate) {
        if (StringUtils.hasText(plateCode)) {
            List<Map<String, Object>> capitalRows = marketFactService.findByTradeDate(FactTable.CAPITAL_FLOW_DAILY_SNAPSHOT, request.getTradeDate());
            if (capitalRows != null) {
                return capitalRows.stream()
                        .filter(row -> "PLATE".equals(normalize(row.get("target_type"))))
                        .filter(row -> plateCode.equals(text(row.get("target_code"), null)))
                        .findFirst()
                        .map(row -> decimal(row, "main_net_inflow"))
                        .orElse(decimal(plate, "main_net_inflow"));
            }
        }
        return decimal(plate, "main_net_inflow");
    }

    private BigDecimal normalizeScore(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return value.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
        }
        return value.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
}
