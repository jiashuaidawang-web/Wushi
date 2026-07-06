package com.wushi.module.similarity.engine.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.SimilarityMatchType;
import com.wushi.common.enums.TargetType;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.similarity.engine.HistoricalSimilarityEngine;
import com.wushi.module.similarity.model.HistoricalSimilarityMatch;
import com.wushi.module.similarity.model.SimilarityFactorDetail;
import com.wushi.module.similarity.model.SimilarityForwardPerformance;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DefaultHistoricalSimilarityEngine implements HistoricalSimilarityEngine {

    private static final int DEFAULT_LIMIT = 10;

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public List<HistoricalSimilarityMatch> match(EngineRequest request) {
        List<HistoricalSimilarityMatch> persisted = persistedMatches(request);
        if (!persisted.isEmpty()) {
            return persisted;
        }
        return deriveFromEvidence(request);
    }

    private List<HistoricalSimilarityMatch> persistedMatches(EngineRequest request) {
        String sql = """
                select match_id, trade_date, engine_type, target_type, target_code, similar_trade_date,
                       similar_target_code, similarity_score, forward_summary
                from historical_similarity_match
                where trade_date = ? and judgement_mode = ? and rule_version = ?
                  and (? = '' or engine_type = ?)
                  and (? = '' or target_type = ?)
                  and (? = '' or target_code = ?)
                order by similarity_score desc
                limit ?
                """;
        String engine = enumName(request.engineType());
        String targetType = enumName(request.targetType());
        String targetCode = safe(request.targetCode());
        List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList(
                sql,
                request.tradeDate(),
                enumName(request.judgementMode()),
                safe(request.ruleVersion()),
                engine, engine,
                targetType, targetType,
                targetCode, targetCode,
                intParam(request, "limit", DEFAULT_LIMIT)
        );
        return rows.stream().map(row -> toMatch(row, request)).toList();
    }

    private List<HistoricalSimilarityMatch> deriveFromEvidence(EngineRequest request) {
        String sql = """
                with current_factor as (
                    select factor_code, any(factor_name) factor_name, avg(factor_value) current_value, avg(weight) weight
                    from judgement_evidence_item
                    where trade_date = ? and rule_version = ?
                      and (? = '' or engine_type = ?)
                      and (? = '' or target_type = ?)
                      and (? = '' or target_code = ?)
                    group by factor_code
                ),
                historical_factor as (
                    select trade_date, target_code, factor_code, any(factor_name) factor_name,
                           avg(factor_value) historical_value, avg(weight) weight
                    from judgement_evidence_item
                    where trade_date < ? and rule_version = ?
                      and (? = '' or engine_type = ?)
                      and (? = '' or target_type = ?)
                    group by trade_date, target_code, factor_code
                )
                select h.trade_date similar_trade_date, h.target_code similar_target_code,
                       sum(greatest(0, 1 - abs(toFloat64(c.current_value) - toFloat64(h.historical_value)))
                           * greatest(toFloat64(ifNull(c.weight, 0)), 0.01))
                       / nullIf(sum(greatest(toFloat64(ifNull(c.weight, 0)), 0.01)), 0) * 100 similarity_score
                from current_factor c
                inner join historical_factor h on c.factor_code = h.factor_code
                group by h.trade_date, h.target_code
                having count() >= 2
                order by similarity_score desc
                limit ?
                """;
        String engine = enumName(request.engineType());
        String targetType = enumName(request.targetType());
        String targetCode = safe(request.targetCode());
        List<Map<String, Object>> rows = clickHouseJdbcTemplate.queryForList(
                sql,
                request.tradeDate(),
                safe(request.ruleVersion()),
                engine, engine,
                targetType, targetType,
                targetCode, targetCode,
                request.tradeDate(),
                safe(request.ruleVersion()),
                engine, engine,
                targetType, targetType,
                intParam(request, "limit", DEFAULT_LIMIT)
        );
        List<HistoricalSimilarityMatch> matches = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            LocalDate similarDate = localDate(row.get("similar_trade_date"));
            String similarTargetCode = text(row.get("similar_target_code"));
            String matchId = "SIM-" + request.tradeDate() + "-" + similarDate + "-" + safe(similarTargetCode);
            matches.add(new HistoricalSimilarityMatch(
                    matchId,
                    request.tradeDate(),
                    request.engineType() == null ? EngineType.HISTORICAL_SIMILARITY : request.engineType(),
                    matchType(request.engineType()),
                    request.targetType() == null ? TargetType.MARKET : request.targetType(),
                    request.targetCode(),
                    request.targetName(),
                    similarDate,
                    similarTargetCode,
                    similarTargetCode,
                    decimal(row.get("similarity_score")),
                    "由证据因子结构临时匹配，需结合后验表现验证",
                    derivedFactorDetails(request, similarDate, similarTargetCode),
                    forwardPerformances(matchId)
            ));
        }
        return matches;
    }

    private HistoricalSimilarityMatch toMatch(Map<String, Object> row, EngineRequest request) {
        String matchId = text(row.get("match_id"));
        return new HistoricalSimilarityMatch(
                matchId,
                localDate(row.get("trade_date")),
                enumValue(EngineType.class, text(row.get("engine_type")), request.engineType()),
                matchType(enumValue(EngineType.class, text(row.get("engine_type")), request.engineType())),
                enumValue(TargetType.class, text(row.get("target_type")), request.targetType()),
                text(row.get("target_code")),
                request.targetName(),
                localDate(row.get("similar_trade_date")),
                text(row.get("similar_target_code")),
                text(row.get("similar_target_code")),
                decimal(row.get("similarity_score")),
                text(row.get("forward_summary")),
                factorDetails(matchId),
                forwardPerformances(matchId)
        );
    }

    private List<SimilarityFactorDetail> factorDetails(String matchId) {
        String sql = """
                select factor_code, factor_name, current_value, historical_value, similarity_score, weight
                from historical_similarity_factor_detail
                where match_id = ?
                order by weight desc, similarity_score desc
                """;
        return clickHouseJdbcTemplate.queryForList(sql, matchId).stream()
                .map(row -> new SimilarityFactorDetail(
                        text(row.get("factor_code")),
                        text(row.get("factor_name")),
                        decimal(row.get("current_value")),
                        decimal(row.get("historical_value")),
                        decimal(row.get("similarity_score")),
                        decimal(row.get("weight"))
                ))
                .toList();
    }

    private List<SimilarityFactorDetail> derivedFactorDetails(EngineRequest request, LocalDate similarDate, String similarTargetCode) {
        String sql = """
                select c.factor_code factor_code, any(c.factor_name) factor_name,
                       avg(c.factor_value) current_value, avg(h.factor_value) historical_value,
                       sum(greatest(0, 1 - abs(toFloat64(c.factor_value) - toFloat64(h.factor_value)))
                           * greatest(toFloat64(ifNull(c.weight, 0)), 0.01))
                       / nullIf(sum(greatest(toFloat64(ifNull(c.weight, 0)), 0.01)), 0) * 100 similarity_score,
                       avg(c.weight) weight
                from judgement_evidence_item c
                inner join judgement_evidence_item h on c.factor_code = h.factor_code
                where c.trade_date = ? and h.trade_date = ?
                  and c.rule_version = ? and h.rule_version = ?
                  and (? = '' or c.engine_type = ?) and (? = '' or h.engine_type = ?)
                  and (? = '' or h.target_code = ?)
                group by c.factor_code
                order by weight desc, similarity_score desc
                limit 8
                """;
        String engine = enumName(request.engineType());
        return clickHouseJdbcTemplate.queryForList(
                        sql,
                        request.tradeDate(), similarDate,
                        safe(request.ruleVersion()), safe(request.ruleVersion()),
                        engine, engine,
                        engine, engine,
                        safe(similarTargetCode), safe(similarTargetCode)
                )
                .stream()
                .map(row -> new SimilarityFactorDetail(
                        text(row.get("factor_code")),
                        text(row.get("factor_name")),
                        decimal(row.get("current_value")),
                        decimal(row.get("historical_value")),
                        decimal(row.get("similarity_score")),
                        decimal(row.get("weight"))
                ))
                .toList();
    }

    private List<SimilarityForwardPerformance> forwardPerformances(String matchId) {
        String sql = """
                select forward_days, return_pct, max_drawdown_pct, cycle_change, mainline_change, risk_change
                from historical_similarity_forward_performance
                where match_id = ?
                order by forward_days
                """;
        return clickHouseJdbcTemplate.queryForList(sql, matchId).stream()
                .map(row -> new SimilarityForwardPerformance(
                        integer(row.get("forward_days")),
                        decimal(row.get("return_pct")),
                        decimal(row.get("max_drawdown_pct")),
                        text(row.get("cycle_change")),
                        text(row.get("mainline_change")),
                        text(row.get("risk_change"))
                ))
                .toList();
    }

    private int intParam(EngineRequest request, String key, int defaultValue) {
        Object value = request.params() == null ? null : request.params().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, E defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
    }

    private SimilarityMatchType matchType(EngineType engineType) {
        if (engineType == EngineType.CYCLE) {
            return SimilarityMatchType.MARKET_CYCLE;
        }
        if (engineType == EngineType.MAINLINE) {
            return SimilarityMatchType.MAINLINE;
        }
        if (engineType == EngineType.LEADER) {
            return SimilarityMatchType.LEADER;
        }
        if (engineType == EngineType.DIVERGENCE_CONSENSUS) {
            return SimilarityMatchType.DIVERGENCE_CONSENSUS;
        }
        if (engineType == EngineType.RISK) {
            return SimilarityMatchType.RISK;
        }
        return SimilarityMatchType.MIXED;
    }
}
