package com.wushi.module.backtest.service.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.ExperienceAction;
import com.wushi.module.backtest.model.CombinationExperienceUpdateResult;
import com.wushi.module.backtest.model.FactorExperienceUpdateResult;
import com.wushi.module.backtest.service.SystemGrowthQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultSystemGrowthQueryService implements SystemGrowthQueryService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<FactorExperienceUpdateResult> factorResults(LocalDate statDate, String ruleVersion) {
        String sql = """
                select factor_code, engine_type, sample_count, hit_count, miss_count, conflict_hit_count, hit_rate,
                       avg_contribution_score, suggested_weight_delta
                from factor_performance_stat
                where stat_date = ? and rule_version = ?
                order by sample_count desc, hit_rate desc
                limit 50
                """;
        return jdbcTemplate.queryForList(sql, statDate, ruleVersion).stream().map(row -> new FactorExperienceUpdateResult(
                text(row, "factor_code"),
                text(row, "factor_code"),
                enumValue(EngineType.class, text(row, "engine_type"), EngineType.EXPERIENCE),
                integer(row, "sample_count"),
                integer(row, "hit_count"),
                integer(row, "miss_count"),
                integer(row, "conflict_hit_count"),
                decimal(row, "hit_rate"),
                decimal(row, "avg_contribution_score"),
                decimal(row, "suggested_weight_delta"),
                action(decimal(row, "suggested_weight_delta"), decimal(row, "hit_rate"), integer(row, "sample_count"))
        )).toList();
    }

    @Override
    public List<CombinationExperienceUpdateResult> combinationResults(LocalDate statDate, String ruleVersion) {
        String sql = """
                select combination_code, engine_type, sample_count, hit_count, miss_count, hit_rate,
                       avg_forward_return, avg_drawdown, suggested_action
                from factor_combination_performance_stat
                where stat_date = ? and rule_version = ?
                order by sample_count desc, hit_rate desc
                limit 50
                """;
        return jdbcTemplate.queryForList(sql, statDate, ruleVersion).stream().map(row -> new CombinationExperienceUpdateResult(
                text(row, "combination_code"),
                text(row, "combination_code"),
                enumValue(EngineType.class, text(row, "engine_type"), EngineType.EXPERIENCE),
                integer(row, "sample_count"),
                integer(row, "hit_count"),
                integer(row, "miss_count"),
                decimal(row, "hit_rate"),
                decimal(row, "avg_forward_return"),
                decimal(row, "avg_drawdown"),
                enumValue(ExperienceAction.class, text(row, "suggested_action"), action(decimal(row, "hit_rate"), integer(row, "sample_count")))
        )).toList();
    }

    @Override
    public List<String> growthLogs(LocalDate statDate) {
        String sql = """
                select concat(title, '：', content) as log_text
                from system_growth_log
                where trade_date = ?
                order by created_at desc
                limit 100
                """;
        return jdbcTemplate.queryForList(sql, statDate).stream().map(row -> text(row, "log_text")).toList();
    }

    private ExperienceAction action(BigDecimal hitRate, int sampleCount) {
        if (sampleCount < 3) {
            return ExperienceAction.NEED_MANUAL_REVIEW;
        }
        if (hitRate.compareTo(BigDecimal.valueOf(0.65)) >= 0) {
            return ExperienceAction.INCREASE_WEIGHT;
        }
        if (hitRate.compareTo(BigDecimal.valueOf(0.35)) < 0) {
            return ExperienceAction.DECREASE_WEIGHT;
        }
        return ExperienceAction.KEEP;
    }

    private ExperienceAction action(BigDecimal suggestedWeightDelta, BigDecimal hitRate, int sampleCount) {
        if (sampleCount < 3) {
            return ExperienceAction.NEED_MANUAL_REVIEW;
        }
        if (suggestedWeightDelta.compareTo(BigDecimal.ZERO) > 0) {
            return ExperienceAction.INCREASE_WEIGHT;
        }
        if (suggestedWeightDelta.compareTo(BigDecimal.ZERO) < 0) {
            return ExperienceAction.DECREASE_WEIGHT;
        }
        return action(hitRate, sampleCount);
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value);
    }
}
