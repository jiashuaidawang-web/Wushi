package com.wushi.module.backtest.engine.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.ExperienceAction;
import com.wushi.common.enums.ValidationResultType;
import com.wushi.common.model.ForwardValidationResult;
import com.wushi.module.backtest.engine.ExperienceFactorEngine;
import com.wushi.module.backtest.model.CombinationExperienceUpdateResult;
import com.wushi.module.backtest.model.ExperienceUpdateRequest;
import com.wushi.module.backtest.model.ExperienceUpdateResult;
import com.wushi.module.backtest.model.FactorExperienceUpdateResult;
import com.wushi.module.rule.model.candidate.RuleCandidateGenerateRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateVersion;
import com.wushi.module.rule.service.candidate.RuleCandidateVersionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DefaultExperienceFactorEngine implements ExperienceFactorEngine {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate clickHouseJdbcTemplate;
    private final RuleCandidateVersionService ruleCandidateVersionService;

    public DefaultExperienceFactorEngine(JdbcTemplate jdbcTemplate,
                                         @Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate,
                                         RuleCandidateVersionService ruleCandidateVersionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
        this.ruleCandidateVersionService = ruleCandidateVersionService;
    }

    @Override
    public ExperienceUpdateResult updateExperience(ExperienceUpdateRequest request) {
        List<FactorExperienceUpdateResult> factors = aggregateFactors(request);
        List<CombinationExperienceUpdateResult> combinations = aggregateCombinations(request);
        List<String> logs = new ArrayList<>();
        factors.forEach(item -> {
            upsertFactor(request.statDate(), request.ruleVersion(), item);
            logs.add("因子 " + item.factorCode() + " 命中率 " + item.hitRate() + "，建议 " + item.suggestedAction());
        });
        combinations.forEach(item -> {
            upsertCombination(request.statDate(), request.ruleVersion(), item);
            logs.add("组合 " + item.combinationCode() + " 命中率 " + item.hitRate() + "，建议 " + item.suggestedAction());
        });
        logs.forEach(log -> saveGrowthLog(request.statDate(), "FACTOR", log, request.ruleVersion()));
        List<RuleCandidateVersion> candidateVersions = generateRuleCandidates(request, factors, logs);
        return new ExperienceUpdateResult(request.statDate(), request.ruleVersion(), factors, combinations, logs, candidateVersions);
    }

    private List<RuleCandidateVersion> generateRuleCandidates(ExperienceUpdateRequest request,
                                                              List<FactorExperienceUpdateResult> factors,
                                                              List<String> logs) {
        Set<EngineType> candidateEngines = factors.stream()
                .filter(item -> item.sampleCount() >= 3)
                .filter(item -> item.suggestedWeightDelta().compareTo(BigDecimal.ZERO) != 0)
                .map(FactorExperienceUpdateResult::engineType)
                .filter(this::supportsRuleCandidate)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EngineType.class)));
        List<RuleCandidateVersion> candidates = new ArrayList<>();
        for (EngineType engineType : candidateEngines) {
            try {
                RuleCandidateVersion candidate = ruleCandidateVersionService.generateCandidate(new RuleCandidateGenerateRequest(
                        request.statDate(),
                        request.ruleVersion(),
                        engineType,
                        null,
                        "system_experience",
                        "经验统计自动生成候选版本"
                ));
                candidates.add(candidate);
                logs.add("规则候选 " + candidate.ruleVersion() + " 已生成，等待人工批准，engine=" + engineType);
            } catch (RuntimeException ex) {
                logs.add("规则候选生成跳过，engine=" + engineType + "，原因：" + ex.getMessage());
            }
        }
        return candidates;
    }

    private boolean supportsRuleCandidate(EngineType engineType) {
        return engineType == EngineType.CYCLE
                || engineType == EngineType.MAINLINE
                || engineType == EngineType.LEADER
                || engineType == EngineType.DIVERGENCE_CONSENSUS
                || engineType == EngineType.RISK;
    }

    private List<FactorExperienceUpdateResult> aggregateFactors(ExperienceUpdateRequest request) {
        List<FactorExperienceUpdateResult> evidenceLevel = aggregateEvidenceLevelFactors(request);
        List<FactorExperienceUpdateResult> results = new ArrayList<>(evidenceLevel);
        Map<EngineType, List<ForwardValidationResult>> byEngine = new EnumMap<>(EngineType.class);
        if (request.validationResults() != null) {
            request.validationResults().forEach(result -> byEngine.computeIfAbsent(result.getEngineType(), key -> new ArrayList<>()).add(result));
        }
        byEngine.forEach((engine, items) -> {
            int hit = (int) items.stream().filter(this::isHit).count();
            int miss = (int) items.stream().filter(item -> item.getValidationResult() == ValidationResultType.MISS).count();
            BigDecimal hitRate = rate(hit, items.size());
            BigDecimal avgScore = items.stream()
                    .map(ForwardValidationResult::getScoreDelta)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(Math.max(items.size(), 1)), 4, RoundingMode.HALF_UP);
            results.add(new FactorExperienceUpdateResult(
                    engine.name() + "_FORWARD_VALIDATION",
                    engine.name() + " 明日验证",
                    engine,
                    items.size(),
                    hit,
                    miss,
                    (int) items.stream().filter(item -> item.getValidationResult() == ValidationResultType.CONFLICT_HIT).count(),
                    hitRate,
                    avgScore,
                    suggestedDelta(hitRate, avgScore),
                    suggestedAction(hitRate, items.size(), avgScore)
            ));
        });
        return results;
    }

    private List<FactorExperienceUpdateResult> aggregateEvidenceLevelFactors(ExperienceUpdateRequest request) {
        String sql = """
                select any(e.engine_type) engine_type,
                       v.factor_code factor_code,
                       any(e.factor_name) factor_name,
                       count() sample_count,
                       countIf(v.validation_result in ('VALID', 'CONFLICT_VALID', 'WARNING_VALID')) hit_count,
                       countIf(v.validation_result = 'INVALID') miss_count,
                       countIf(v.validation_result = 'CONFLICT_VALID') conflict_hit_count,
                       avg(if(v.validation_result in ('VALID', 'CONFLICT_VALID', 'WARNING_VALID'), 1, 0)) hit_rate,
                       avg(v.contribution_score) avg_contribution_score
                from evidence_validation_item v
                left join judgement_evidence_item e
                  on e.judgement_id = v.judgement_id
                 and e.evidence_id = v.evidence_id
                where v.validation_date = ?
                group by v.factor_code
                order by sample_count desc
                """;
        return clickHouseJdbcTemplate.queryForList(sql, request.statDate()).stream()
                .map(row -> {
                    int sampleCount = integer(row.get("sample_count"));
                    BigDecimal hitRate = decimal(row.get("hit_rate"));
                    return new FactorExperienceUpdateResult(
                            text(row.get("factor_code")),
                            text(row.get("factor_name")),
                            enumValue(EngineType.class, text(row.get("engine_type")), EngineType.EXPERIENCE),
                            sampleCount,
                            integer(row.get("hit_count")),
                            integer(row.get("miss_count")),
                            integer(row.get("conflict_hit_count")),
                            hitRate,
                            decimal(row.get("avg_contribution_score")),
                            suggestedDelta(hitRate, decimal(row.get("avg_contribution_score"))),
                            suggestedAction(hitRate, sampleCount, decimal(row.get("avg_contribution_score")))
                    );
                })
                .toList();
    }

    private List<CombinationExperienceUpdateResult> aggregateCombinations(ExperienceUpdateRequest request) {
        if (request.validationResults() == null || request.validationResults().isEmpty()) {
            return List.of();
        }
        int count = request.validationResults().size();
        int hit = (int) request.validationResults().stream().filter(this::isHit).count();
        int miss = (int) request.validationResults().stream().filter(item -> item.getValidationResult() == ValidationResultType.MISS).count();
        BigDecimal hitRate = rate(hit, count);
        BigDecimal avgReturn = request.validationResults().stream()
                .map(ForwardValidationResult::getReturnPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
        BigDecimal avgDrawdown = request.validationResults().stream()
                .map(ForwardValidationResult::getMaxDrawdownPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
        return List.of(new CombinationExperienceUpdateResult(
                "DAILY_JUDGEMENT_CHAIN",
                "判断-验证-反馈链路",
                EngineType.EXPERIENCE,
                count,
                hit,
                miss,
                hitRate,
                avgReturn,
                avgDrawdown,
                suggestedAction(hitRate, count)
        ));
    }

    private void upsertFactor(LocalDate statDate, String ruleVersion, FactorExperienceUpdateResult item) {
        String sql = """
                insert into factor_performance_stat
                (stat_date, rule_version, engine_type, factor_code, sample_count, hit_count, miss_count,
                 conflict_hit_count, hit_rate, avg_contribution_score, suggested_weight_delta)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update sample_count = values(sample_count), hit_count = values(hit_count),
                miss_count = values(miss_count), conflict_hit_count = values(conflict_hit_count), hit_rate = values(hit_rate),
                avg_contribution_score = values(avg_contribution_score), suggested_weight_delta = values(suggested_weight_delta)
                """;
        jdbcTemplate.update(sql, statDate, ruleVersion, item.engineType().name(), item.factorCode(), item.sampleCount(),
                item.hitCount(), item.missCount(), item.conflictHitCount(), item.hitRate(), item.avgContributionScore(), item.suggestedWeightDelta());
    }

    private void upsertCombination(LocalDate statDate, String ruleVersion, CombinationExperienceUpdateResult item) {
        String sql = """
                insert into factor_combination_performance_stat
                (stat_date, rule_version, engine_type, combination_code, sample_count, hit_count, miss_count,
                 hit_rate, avg_forward_return, avg_drawdown, suggested_action)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update sample_count = values(sample_count), hit_count = values(hit_count),
                miss_count = values(miss_count), hit_rate = values(hit_rate),
                avg_forward_return = values(avg_forward_return), avg_drawdown = values(avg_drawdown),
                suggested_action = values(suggested_action)
                """;
        jdbcTemplate.update(sql, statDate, ruleVersion, item.engineType().name(), item.combinationCode(), item.sampleCount(),
                item.hitCount(), item.missCount(), item.hitRate(), item.avgForwardReturn(), item.avgDrawdown(),
                item.suggestedAction().name());
    }

    private void saveGrowthLog(LocalDate statDate, String type, String content, String ruleVersion) {
        String sql = """
                insert into system_growth_log
                (growth_id, trade_date, growth_type, engine_type, title, content, before_value, after_value, source_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, "GROWTH-" + UUID.randomUUID(), statDate, type, EngineType.EXPERIENCE.name(),
                "经验因子更新", content, null, ruleVersion, "forward_validation");
    }

    private boolean isHit(ForwardValidationResult result) {
        return result.getValidationResult() == ValidationResultType.HIT || result.getValidationResult() == ValidationResultType.CONFLICT_HIT;
    }

    private BigDecimal rate(int hit, int count) {
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(hit).divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal suggestedDelta(BigDecimal hitRate) {
        return suggestedDelta(hitRate, BigDecimal.ZERO);
    }

    private BigDecimal suggestedDelta(BigDecimal hitRate, BigDecimal avgContributionScore) {
        if (avgContributionScore.compareTo(new BigDecimal("-0.2500")) <= 0) {
            return BigDecimal.valueOf(-0.05);
        }
        if (avgContributionScore.compareTo(new BigDecimal("0.5000")) >= 0 && hitRate.compareTo(BigDecimal.valueOf(0.45)) >= 0) {
            return BigDecimal.valueOf(0.05);
        }
        if (hitRate.compareTo(BigDecimal.valueOf(0.65)) >= 0) {
            return BigDecimal.valueOf(0.05);
        }
        if (hitRate.compareTo(BigDecimal.valueOf(0.45)) < 0) {
            return BigDecimal.valueOf(-0.05);
        }
        return BigDecimal.ZERO;
    }

    private ExperienceAction suggestedAction(BigDecimal hitRate, int count) {
        return suggestedAction(hitRate, count, BigDecimal.ZERO);
    }

    private ExperienceAction suggestedAction(BigDecimal hitRate, int count, BigDecimal avgContributionScore) {
        if (count < 3) {
            return ExperienceAction.NEED_MANUAL_REVIEW;
        }
        if (avgContributionScore.compareTo(new BigDecimal("-0.2500")) <= 0) {
            return ExperienceAction.DECREASE_WEIGHT;
        }
        if (avgContributionScore.compareTo(new BigDecimal("0.5000")) >= 0 && hitRate.compareTo(BigDecimal.valueOf(0.45)) >= 0) {
            return ExperienceAction.INCREASE_WEIGHT;
        }
        if (hitRate.compareTo(BigDecimal.valueOf(0.65)) >= 0) {
            return ExperienceAction.INCREASE_WEIGHT;
        }
        if (hitRate.compareTo(BigDecimal.valueOf(0.35)) < 0) {
            return ExperienceAction.DECREASE_WEIGHT;
        }
        return ExperienceAction.KEEP;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimal(Object value) {
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
