package com.wushi.module.mainline.service.impl;

import com.wushi.module.mainline.model.MainlineCandidate;
import com.wushi.module.mainline.service.MainlineCandidateSelector;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.service.MarketFactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class DefaultMainlineCandidateSelector implements MainlineCandidateSelector {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final MarketFactService marketFactService;

    @Override
    public List<MainlineCandidate> selectCandidates(LocalDate tradeDate, int limit) {
        if (tradeDate == null || limit <= 0) {
            return List.of();
        }
        List<Map<String, Object>> rows = marketFactService.findByTradeDate(FactTable.PLATE_DAILY_SNAPSHOT, tradeDate);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        AtomicInteger rank = new AtomicInteger(1);
        return rows.stream()
                .filter(this::hasPlateCode)
                .map(row -> new ScoredPlate(row, score(row)))
                .filter(scored -> scored.score().compareTo(ZERO) > 0)
                .sorted(Comparator.comparing(ScoredPlate::score).reversed())
                .limit(limit)
                .map(scored -> toCandidate(rank.getAndIncrement(), scored))
                .toList();
    }

    private MainlineCandidate toCandidate(int rank, ScoredPlate scored) {
        Map<String, Object> row = scored.row();
        return new MainlineCandidate(
                rank,
                text(row.get("plate_code")),
                text(row.get("plate_name")),
                text(row.get("plate_type")),
                scored.score(),
                reason(row, scored.score())
        );
    }

    private BigDecimal score(Map<String, Object> row) {
        BigDecimal limitUpScore = cap(defaultZero(decimal(row, "limit_up_count")).divide(new BigDecimal("20"), 4, RoundingMode.HALF_UP))
                .multiply(new BigDecimal("30"));
        BigDecimal ladderScore = normalizeScore(decimal(row, "ladder_integrity_score")).multiply(new BigDecimal("20"));
        BigDecimal sustainScore = normalizeScore(decimal(row, "sustainability_score")).multiply(new BigDecimal("20"));
        BigDecimal capitalScore = capitalScore(decimal(row, "main_net_inflow")).multiply(new BigDecimal("15"));
        BigDecimal middleArmyScore = middleArmyScore(row).multiply(new BigDecimal("10"));
        BigDecimal breadthScore = breadthScore(row).multiply(new BigDecimal("5"));
        BigDecimal rearRiskPenalty = rearRisk(row).multiply(new BigDecimal("25"));

        BigDecimal score = limitUpScore
                .add(ladderScore)
                .add(sustainScore)
                .add(capitalScore)
                .add(middleArmyScore)
                .add(breadthScore)
                .subtract(rearRiskPenalty);
        return score.max(ZERO).min(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
    }

    private String reason(Map<String, Object> row, BigDecimal score) {
        BigDecimal limitUp = defaultZero(decimal(row, "limit_up_count"));
        BigDecimal ladder = normalizeScore(decimal(row, "ladder_integrity_score"));
        BigDecimal sustain = normalizeScore(decimal(row, "sustainability_score"));
        BigDecimal capital = defaultZero(decimal(row, "main_net_inflow"));
        BigDecimal risk = rearRisk(row);

        if (score.compareTo(new BigDecimal("70")) >= 0 && ladder.compareTo(new BigDecimal("0.6000")) >= 0
                && sustain.compareTo(new BigDecimal("0.6000")) >= 0 && risk.compareTo(new BigDecimal("0.3500")) <= 0) {
            return "主线确认候选：涨停强度、持续性、梯队和风险约束同时占优。";
        }
        if (limitUp.compareTo(new BigDecimal("5")) >= 0 && ladder.compareTo(new BigDecimal("0.4500")) >= 0) {
            return "主线竞争候选：涨停强度和梯队已经出现，但还需要验证中军承接与后排风险。";
        }
        if (capital.compareTo(BigDecimal.ZERO) > 0 && sustain.compareTo(new BigDecimal("0.4000")) >= 0) {
            return "资金回流候选：资金开始聚焦，但涨停梯队或带动性还不完整。";
        }
        if (limitUp.compareTo(BigDecimal.ZERO) > 0) {
            return "题材轮动候选：有短线点火，暂未证明持续接力。";
        }
        return "弱候选：缺少涨停梯队和持续资金合力，优先按支线/噪音观察。";
    }

    private BigDecimal middleArmyScore(Map<String, Object> row) {
        BigDecimal amount = defaultZero(decimal(row, "amount"));
        BigDecimal amountScore = cap(amount.divide(new BigDecimal("10000000000"), 4, RoundingMode.HALF_UP));
        BigDecimal upCount = defaultZero(decimal(row, "up_count"));
        BigDecimal stockCount = defaultZero(decimal(row, "stock_count"));
        BigDecimal upRatio = stockCount.compareTo(BigDecimal.ZERO) > 0
                ? upCount.divide(stockCount, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return amountScore.multiply(new BigDecimal("0.6000"))
                .add(cap(upRatio).multiply(new BigDecimal("0.4000")))
                .min(ONE)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal breadthScore(Map<String, Object> row) {
        BigDecimal upCount = defaultZero(decimal(row, "up_count"));
        BigDecimal downCount = defaultZero(decimal(row, "down_count"));
        BigDecimal total = upCount.add(downCount);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return cap(upCount.divide(total, 4, RoundingMode.HALF_UP));
    }

    private BigDecimal capitalScore(BigDecimal capital) {
        if (capital == null || capital.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return cap(capital.divide(new BigDecimal("1000000000"), 4, RoundingMode.HALF_UP));
    }

    private BigDecimal rearRisk(Map<String, Object> row) {
        BigDecimal broken = defaultZero(decimal(row, "broken_limit_count"));
        BigDecimal limitDown = defaultZero(decimal(row, "limit_down_count"));
        BigDecimal limitUp = defaultZero(decimal(row, "limit_up_count"));
        BigDecimal denominator = limitUp.add(broken).add(limitDown);
        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return ZERO;
        }
        return cap(broken.add(limitDown).divide(denominator, 4, RoundingMode.HALF_UP));
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

    private boolean hasPlateCode(Map<String, Object> row) {
        return StringUtils.hasText(text(row.get("plate_code")));
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
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

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String text(Object value) {
        return Objects.toString(value, "").trim();
    }

    private record ScoredPlate(Map<String, Object> row, BigDecimal score) {
    }
}
