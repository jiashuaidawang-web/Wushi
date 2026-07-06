package com.wushi.module.rule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wushi.common.enums.EngineType;
import com.wushi.module.rule.domain.entity.FactorCombinationDefinitionEntity;
import com.wushi.module.rule.domain.entity.RuleFactorWeightEntity;
import com.wushi.module.rule.domain.entity.RuleVersionCandidateEntity;
import com.wushi.module.rule.domain.entity.RuleVersionCandidateFactorEntity;
import com.wushi.module.rule.domain.entity.RuleVersionEntity;
import com.wushi.module.rule.mapper.FactorCombinationDefinitionMapper;
import com.wushi.module.rule.mapper.RuleFactorWeightMapper;
import com.wushi.module.rule.mapper.RuleVersionCandidateFactorMapper;
import com.wushi.module.rule.mapper.RuleVersionCandidateMapper;
import com.wushi.module.rule.mapper.RuleVersionMapper;
import com.wushi.module.rule.model.RuleEvolutionApprovalRequest;
import com.wushi.module.rule.model.RuleEvolutionGenerateRequest;
import com.wushi.module.rule.model.RuleVersionCandidateDetail;
import com.wushi.module.rule.model.RuleVersionCandidateFactorChange;
import com.wushi.module.rule.service.RuleConfigService;
import com.wushi.module.rule.service.RuleEvolutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultRuleEvolutionService implements RuleEvolutionService {

    private static final String GENERATED = "GENERATED";
    private static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String EFFECTIVE = "EFFECTIVE";
    private static final String DRAFT = "DRAFT";
    private static final String ACTIVE = "ACTIVE";
    private static final String ARCHIVED = "ARCHIVED";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal MIN_WEIGHT = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("3.0000");

    private final RuleVersionMapper ruleVersionMapper;
    private final RuleFactorWeightMapper ruleFactorWeightMapper;
    private final FactorCombinationDefinitionMapper factorCombinationDefinitionMapper;
    private final RuleVersionCandidateMapper candidateMapper;
    private final RuleVersionCandidateFactorMapper candidateFactorMapper;
    private final RuleConfigService ruleConfigService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public List<RuleVersionCandidateDetail> generateCandidates(RuleEvolutionGenerateRequest request) {
        LocalDate statDate = request.statDate() == null ? LocalDate.now() : request.statDate();
        List<EngineType> engines = request.engineTypes() == null || request.engineTypes().isEmpty()
                ? defaultEngines()
                : request.engineTypes();

        return engines.stream()
                .map(engineType -> generateOne(request, statDate, engineType))
                .filter(detail -> detail != null)
                .toList();
    }

    @Override
    public List<RuleVersionCandidateDetail> listCandidates(String status, LocalDate statDate, String ruleVersion) {
        LambdaQueryWrapper<RuleVersionCandidateEntity> wrapper = new LambdaQueryWrapper<RuleVersionCandidateEntity>()
                .orderByDesc(RuleVersionCandidateEntity::getCreatedAt)
                .orderByDesc(RuleVersionCandidateEntity::getId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(RuleVersionCandidateEntity::getStatus, status);
        }
        if (statDate != null) {
            wrapper.eq(RuleVersionCandidateEntity::getStatDate, statDate);
        }
        if (StringUtils.hasText(ruleVersion)) {
            wrapper.eq(RuleVersionCandidateEntity::getBaseRuleVersion, ruleVersion);
        }
        return candidateMapper.selectList(wrapper).stream().map(this::toDetail).toList();
    }

    @Override
    public RuleVersionCandidateDetail detail(String candidateId) {
        return toDetail(requireCandidate(candidateId));
    }

    @Override
    @Transactional
    public RuleVersionCandidateDetail approve(RuleEvolutionApprovalRequest request) {
        RuleVersionCandidateEntity candidate = requireCandidate(request.candidateId());
        if (!PENDING_APPROVAL.equals(candidate.getStatus()) && !GENERATED.equals(candidate.getStatus())) {
            throw new IllegalStateException("Only generated or pending candidates can be approved: " + candidate.getStatus());
        }

        candidate.setStatus(APPROVED);
        candidate.setApprovedBy(request.resolvedOperator());
        candidate.setApprovalComment(request.approvalComment());
        candidate.setApprovedAt(LocalDateTime.now());
        createDraftRule(candidate, request.resolvedOperator());
        candidateMapper.updateById(candidate);

        growth(candidate.getStatDate(), "RULE", candidate.getEngineType(),
                "规则候选已批准",
                "候选版本 " + candidate.getTargetRuleVersion() + " 已进入 DRAFT，等待人工生效。",
                candidate.getBaseRuleVersion(), candidate.getTargetRuleVersion(), candidate.getCandidateId());
        return toDetail(candidate);
    }

    @Override
    @Transactional
    public RuleVersionCandidateDetail reject(RuleEvolutionApprovalRequest request) {
        RuleVersionCandidateEntity candidate = requireCandidate(request.candidateId());
        if (EFFECTIVE.equals(candidate.getStatus())) {
            throw new IllegalStateException("Effective candidate cannot be rejected.");
        }
        candidate.setStatus(REJECTED);
        candidate.setApprovedBy(request.resolvedOperator());
        candidate.setApprovalComment(request.approvalComment());
        candidate.setApprovedAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);
        growth(candidate.getStatDate(), "RULE", candidate.getEngineType(),
                "规则候选已拒绝",
                "候选版本 " + candidate.getTargetRuleVersion() + " 被拒绝，原因：" + safe(request.approvalComment(), "未填写"),
                candidate.getTargetRuleVersion(), REJECTED, candidate.getCandidateId());
        return toDetail(candidate);
    }

    @Override
    @Transactional
    public RuleVersionCandidateDetail activate(RuleEvolutionApprovalRequest request) {
        RuleVersionCandidateEntity candidate = requireCandidate(request.candidateId());
        if (!APPROVED.equals(candidate.getStatus())) {
            throw new IllegalStateException("Only approved candidates can be activated: " + candidate.getStatus());
        }
        RuleVersionEntity draft = ruleVersionMapper.selectOne(new LambdaQueryWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, candidate.getEngineType())
                .eq(RuleVersionEntity::getRuleVersion, candidate.getTargetRuleVersion())
                .last("LIMIT 1"));
        if (draft == null) {
            createDraftRule(candidate, request.resolvedOperator());
        }

        ruleVersionMapper.update(null, new LambdaUpdateWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, candidate.getEngineType())
                .eq(RuleVersionEntity::getStatus, ACTIVE)
                .set(RuleVersionEntity::getStatus, ARCHIVED));
        ruleVersionMapper.update(null, new LambdaUpdateWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, candidate.getEngineType())
                .eq(RuleVersionEntity::getRuleVersion, candidate.getTargetRuleVersion())
                .set(RuleVersionEntity::getStatus, ACTIVE)
                .set(RuleVersionEntity::getEffectiveDate, LocalDate.now()));

        candidate.setStatus(EFFECTIVE);
        candidate.setApprovedBy(request.resolvedOperator());
        candidate.setApprovalComment(request.approvalComment());
        candidate.setEffectiveAt(LocalDateTime.now());
        candidateMapper.updateById(candidate);

        growth(candidate.getStatDate(), "RULE", candidate.getEngineType(),
                "规则版本已生效",
                "引擎 " + candidate.getEngineType() + " 已从 " + candidate.getBaseRuleVersion()
                        + " 切换到 " + candidate.getTargetRuleVersion() + "，后续推演将读取新 ACTIVE 版本。",
                candidate.getBaseRuleVersion(), candidate.getTargetRuleVersion(), candidate.getCandidateId());
        return toDetail(candidate);
    }

    private RuleVersionCandidateDetail generateOne(RuleEvolutionGenerateRequest request, LocalDate statDate, EngineType engineType) {
        String baseRuleVersion = StringUtils.hasText(request.baseRuleVersion())
                ? request.baseRuleVersion()
                : ruleConfigService.findActiveRuleVersion(engineType).orElse(null);
        if (!StringUtils.hasText(baseRuleVersion)) {
            return null;
        }

        List<RuleFactorWeightEntity> currentWeights = ruleFactorWeightMapper.selectList(new LambdaQueryWrapper<RuleFactorWeightEntity>()
                .eq(RuleFactorWeightEntity::getRuleVersion, baseRuleVersion)
                .eq(RuleFactorWeightEntity::getEngineType, engineType.name())
                .eq(RuleFactorWeightEntity::getEnabled, 1)
                .orderByAsc(RuleFactorWeightEntity::getFactorCode));
        if (currentWeights.isEmpty()) {
            return null;
        }
        Map<String, RuleFactorWeightEntity> weightMap = currentWeights.stream()
                .collect(Collectors.toMap(RuleFactorWeightEntity::getFactorCode, Function.identity(), (left, right) -> left));
        List<Map<String, Object>> stats = factorStats(statDate, baseRuleVersion, engineType, request.resolvedMinSampleCount());
        List<RuleVersionCandidateFactorEntity> factorChanges = stats.stream()
                .map(row -> toFactorCandidate(row, weightMap.get(text(row, "factor_code")), request.resolvedMaxAbsDeltaPerFactor()))
                .filter(change -> change != null && change.getSuggestedDelta().compareTo(ZERO) != 0)
                .sorted(Comparator.comparing(RuleVersionCandidateFactorEntity::getFactorCode))
                .toList();
        List<Map<String, Object>> combinationStats = combinationStats(statDate, baseRuleVersion, engineType, request.resolvedMinSampleCount());
        if (factorChanges.isEmpty() && actionableCombinationStats(combinationStats).isEmpty()) {
            return null;
        }

        String candidateId = "RVC-" + statDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + engineType.name() + "-" + shortUuid();
        String targetRuleVersion = targetRuleVersion(baseRuleVersion, statDate, engineType);
        BigDecimal totalAbsDelta = factorChanges.stream()
                .map(change -> change.getSuggestedDelta().abs())
                .reduce(ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        int sampleCount = factorChanges.stream().mapToInt(RuleVersionCandidateFactorEntity::getSampleCount).sum()
                + combinationStats.stream().mapToInt(row -> integer(row, "sample_count")).sum();

        RuleVersionCandidateEntity candidate = new RuleVersionCandidateEntity();
        candidate.setCandidateId(candidateId);
        candidate.setBaseRuleVersion(baseRuleVersion);
        candidate.setTargetRuleVersion(targetRuleVersion);
        candidate.setEngineType(engineType.name());
        candidate.setStatus(PENDING_APPROVAL);
        candidate.setStatDate(statDate);
        candidate.setFactorChangeCount(factorChanges.size());
        candidate.setSampleCount(sampleCount);
        candidate.setTotalAbsDelta(totalAbsDelta);
        candidate.setReasonSummary(reasonSummary(engineType, factorChanges, combinationStats));
        candidate.setRiskSummary(riskSummary(request.resolvedMinSampleCount(), request.resolvedMaxAbsDeltaPerFactor(),
                combinationStats, growthHighlights(statDate, engineType)));
        candidate.setGeneratedBy(request.resolvedGeneratedBy());
        candidateMapper.insert(candidate);

        for (RuleVersionCandidateFactorEntity factorChange : factorChanges) {
            factorChange.setCandidateId(candidateId);
            factorChange.setEngineType(engineType.name());
            candidateFactorMapper.insert(factorChange);
        }

        growth(statDate, "RULE", engineType.name(),
                "生成规则候选版本",
                candidate.getReasonSummary(),
                baseRuleVersion, targetRuleVersion, candidateId);
        return toDetail(candidate);
    }

    private RuleVersionCandidateFactorEntity toFactorCandidate(Map<String, Object> row, RuleFactorWeightEntity currentWeight,
                                                               BigDecimal maxAbsDelta) {
        if (currentWeight == null) {
            return null;
        }
        BigDecimal rawDelta = decimal(row, "suggested_weight_delta");
        BigDecimal delta = clamp(rawDelta, maxAbsDelta);
        if (delta.compareTo(ZERO) == 0) {
            return null;
        }
        BigDecimal suggestedWeight = currentWeight.getWeight().add(delta);
        if (suggestedWeight.compareTo(MIN_WEIGHT) < 0) {
            suggestedWeight = MIN_WEIGHT;
            delta = suggestedWeight.subtract(currentWeight.getWeight());
        }
        if (suggestedWeight.compareTo(MAX_WEIGHT) > 0) {
            suggestedWeight = MAX_WEIGHT;
            delta = suggestedWeight.subtract(currentWeight.getWeight());
        }
        RuleVersionCandidateFactorEntity entity = new RuleVersionCandidateFactorEntity();
        entity.setFactorCode(currentWeight.getFactorCode());
        entity.setFactorName(text(row, "factor_name"));
        entity.setCurrentWeight(scale(currentWeight.getWeight()));
        entity.setSuggestedDelta(scale(delta));
        entity.setSuggestedWeight(scale(suggestedWeight));
        entity.setThresholdValue(currentWeight.getThresholdValue());
        entity.setThresholdOperator(currentWeight.getThresholdOperator());
        entity.setEvidenceType(currentWeight.getEvidenceType());
        entity.setSampleCount(integer(row, "sample_count"));
        entity.setHitCount(integer(row, "hit_count"));
        entity.setMissCount(integer(row, "miss_count"));
        entity.setConflictHitCount(integer(row, "conflict_hit_count"));
        entity.setHitRate(decimal(row, "hit_rate"));
        entity.setAvgContributionScore(decimal(row, "avg_contribution_score"));
        entity.setSuggestedAction(action(delta, entity.getSampleCount()));
        entity.setChangeReason(changeReason(entity, row));
        return entity;
    }

    private void createDraftRule(RuleVersionCandidateEntity candidate, String operator) {
        RuleVersionEntity existing = ruleVersionMapper.selectOne(new LambdaQueryWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getRuleVersion, candidate.getTargetRuleVersion())
                .eq(RuleVersionEntity::getEngineType, candidate.getEngineType())
                .last("LIMIT 1"));
        if (existing == null) {
            RuleVersionEntity rule = new RuleVersionEntity();
            rule.setRuleVersion(candidate.getTargetRuleVersion());
            rule.setRuleName(candidate.getEngineType() + " 经验演进版本");
            rule.setEngineType(candidate.getEngineType());
            rule.setStatus(DRAFT);
            rule.setDescription(candidate.getReasonSummary() + "\n" + safe(candidate.getRiskSummary(), ""));
            rule.setSourceRuleVersion(candidate.getBaseRuleVersion());
            rule.setCandidateStatDate(candidate.getStatDate());
            rule.setEffectiveDate(null);
            rule.setApprovedBy(candidate.getApprovedBy());
            rule.setApprovedAt(candidate.getApprovedAt());
            rule.setApprovalRemark(candidate.getApprovalComment());
            rule.setCreatedBy(operator);
            ruleVersionMapper.insert(rule);
        }

        List<RuleVersionCandidateFactorEntity> changes = candidateFactorMapper.selectList(new LambdaQueryWrapper<RuleVersionCandidateFactorEntity>()
                .eq(RuleVersionCandidateFactorEntity::getCandidateId, candidate.getCandidateId()));
        Map<String, RuleVersionCandidateFactorEntity> changeMap = changes.stream()
                .collect(Collectors.toMap(RuleVersionCandidateFactorEntity::getFactorCode, Function.identity(), (left, right) -> left));

        ruleFactorWeightMapper.delete(new LambdaQueryWrapper<RuleFactorWeightEntity>()
                .eq(RuleFactorWeightEntity::getRuleVersion, candidate.getTargetRuleVersion())
                .eq(RuleFactorWeightEntity::getEngineType, candidate.getEngineType()));
        List<RuleFactorWeightEntity> baseWeights = ruleFactorWeightMapper.selectList(new LambdaQueryWrapper<RuleFactorWeightEntity>()
                .eq(RuleFactorWeightEntity::getRuleVersion, candidate.getBaseRuleVersion())
                .eq(RuleFactorWeightEntity::getEngineType, candidate.getEngineType()));
        for (RuleFactorWeightEntity baseWeight : baseWeights) {
            RuleFactorWeightEntity next = copyWeight(baseWeight, candidate.getTargetRuleVersion(), changeMap.get(baseWeight.getFactorCode()));
            ruleFactorWeightMapper.insert(next);
        }
        copyCombinations(candidate);
    }

    private void copyCombinations(RuleVersionCandidateEntity candidate) {
        factorCombinationDefinitionMapper.delete(new LambdaQueryWrapper<FactorCombinationDefinitionEntity>()
                .eq(FactorCombinationDefinitionEntity::getRuleVersion, candidate.getTargetRuleVersion())
                .eq(FactorCombinationDefinitionEntity::getEngineType, candidate.getEngineType()));
        List<FactorCombinationDefinitionEntity> baseCombinations = factorCombinationDefinitionMapper.selectList(
                new LambdaQueryWrapper<FactorCombinationDefinitionEntity>()
                        .eq(FactorCombinationDefinitionEntity::getRuleVersion, candidate.getBaseRuleVersion())
                        .eq(FactorCombinationDefinitionEntity::getEngineType, candidate.getEngineType()));
        for (FactorCombinationDefinitionEntity base : baseCombinations) {
            FactorCombinationDefinitionEntity next = new FactorCombinationDefinitionEntity();
            next.setCombinationCode(base.getCombinationCode());
            next.setCombinationName(base.getCombinationName());
            next.setEngineType(base.getEngineType());
            next.setRuleVersion(candidate.getTargetRuleVersion());
            next.setFactorCodes(base.getFactorCodes());
            next.setConditionExpression(base.getConditionExpression());
            next.setExpectedMeaning(base.getExpectedMeaning());
            next.setEnabled(disableCombination(candidate, base.getCombinationCode()) ? 0 : base.getEnabled());
            factorCombinationDefinitionMapper.insert(next);
        }
    }

    private RuleFactorWeightEntity copyWeight(RuleFactorWeightEntity baseWeight, String targetRuleVersion,
                                              RuleVersionCandidateFactorEntity change) {
        RuleFactorWeightEntity next = new RuleFactorWeightEntity();
        next.setRuleVersion(targetRuleVersion);
        next.setEngineType(baseWeight.getEngineType());
        next.setFactorCode(baseWeight.getFactorCode());
        next.setWeight(change == null ? baseWeight.getWeight() : change.getSuggestedWeight());
        next.setThresholdValue(baseWeight.getThresholdValue());
        next.setThresholdOperator(baseWeight.getThresholdOperator());
        next.setEvidenceType(baseWeight.getEvidenceType());
        next.setEnabled(baseWeight.getEnabled());
        return next;
    }

    private RuleVersionCandidateEntity requireCandidate(String candidateId) {
        if (!StringUtils.hasText(candidateId)) {
            throw new IllegalArgumentException("candidateId is required.");
        }
        RuleVersionCandidateEntity candidate = candidateMapper.selectOne(new LambdaQueryWrapper<RuleVersionCandidateEntity>()
                .eq(RuleVersionCandidateEntity::getCandidateId, candidateId)
                .last("LIMIT 1"));
        if (candidate == null) {
            throw new IllegalArgumentException("Rule version candidate not found: " + candidateId);
        }
        return candidate;
    }

    private RuleVersionCandidateDetail toDetail(RuleVersionCandidateEntity candidate) {
        List<RuleVersionCandidateFactorChange> changes = candidateFactorMapper.selectList(
                        new LambdaQueryWrapper<RuleVersionCandidateFactorEntity>()
                                .eq(RuleVersionCandidateFactorEntity::getCandidateId, candidate.getCandidateId())
                                .orderByDesc(RuleVersionCandidateFactorEntity::getSampleCount)
                                .orderByDesc(RuleVersionCandidateFactorEntity::getSuggestedDelta))
                .stream()
                .map(this::toChange)
                .toList();
        return new RuleVersionCandidateDetail(
                candidate.getCandidateId(),
                candidate.getBaseRuleVersion(),
                candidate.getTargetRuleVersion(),
                enumValue(EngineType.class, candidate.getEngineType()),
                candidate.getStatus(),
                candidate.getStatDate(),
                candidate.getFactorChangeCount(),
                candidate.getSampleCount(),
                candidate.getTotalAbsDelta(),
                candidate.getReasonSummary(),
                candidate.getRiskSummary(),
                candidate.getGeneratedBy(),
                candidate.getApprovedBy(),
                candidate.getApprovalComment(),
                candidate.getApprovedAt(),
                candidate.getEffectiveAt(),
                changes
        );
    }

    private RuleVersionCandidateFactorChange toChange(RuleVersionCandidateFactorEntity entity) {
        return new RuleVersionCandidateFactorChange(
                entity.getFactorCode(),
                safe(entity.getFactorName(), entity.getFactorCode()),
                entity.getCurrentWeight(),
                entity.getSuggestedDelta(),
                entity.getSuggestedWeight(),
                entity.getSampleCount(),
                entity.getHitCount(),
                entity.getMissCount(),
                entity.getConflictHitCount(),
                entity.getHitRate(),
                entity.getAvgContributionScore(),
                entity.getSuggestedAction(),
                entity.getChangeReason()
        );
    }

    private List<Map<String, Object>> factorStats(LocalDate statDate, String ruleVersion, EngineType engineType, int minSampleCount) {
        String sql = """
                select s.factor_code,
                       max(f.factor_name) factor_name,
                       s.sample_count,
                       s.hit_count,
                       s.miss_count,
                       s.conflict_hit_count,
                       s.hit_rate,
                       s.avg_contribution_score,
                       s.suggested_weight_delta
                from factor_performance_stat s
                left join factor_definition f on f.factor_code = s.factor_code and f.engine_type = s.engine_type
                where s.stat_date = ? and s.rule_version = ? and s.engine_type = ? and s.sample_count >= ?
                group by s.factor_code, s.sample_count, s.hit_count, s.miss_count, s.conflict_hit_count,
                         s.hit_rate, s.avg_contribution_score, s.suggested_weight_delta
                order by abs(s.suggested_weight_delta) desc, s.sample_count desc
                """;
        return jdbcTemplate.queryForList(sql, statDate, ruleVersion, engineType.name(), minSampleCount);
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal maxAbsDelta) {
        BigDecimal max = scale(maxAbsDelta).abs();
        BigDecimal scaled = scale(value);
        if (scaled.compareTo(max) > 0) {
            return max;
        }
        if (scaled.compareTo(max.negate()) < 0) {
            return max.negate();
        }
        return scaled;
    }

    private String targetRuleVersion(String baseRuleVersion, LocalDate statDate, EngineType engineType) {
        String suffix = statDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + engineType.name().toLowerCase() + "-" + shortUuid();
        String target = baseRuleVersion + "-exp-" + suffix;
        return target.length() <= 64 ? target : "exp-" + suffix;
    }

    private String reasonSummary(EngineType engineType, List<RuleVersionCandidateFactorEntity> factorChanges,
                                 List<Map<String, Object>> combinationStats) {
        String top = factorChanges.stream()
                .sorted(Comparator.comparing((RuleVersionCandidateFactorEntity item) -> item.getSuggestedDelta().abs()).reversed())
                .limit(5)
                .map(item -> item.getFactorCode() + " " + sign(item.getSuggestedDelta()) + item.getSuggestedDelta().abs())
                .collect(Collectors.joining("，"));
        String combinations = actionableCombinationStats(combinationStats).stream()
                .limit(3)
                .map(row -> text(row, "combination_code") + ":" + text(row, "suggested_action"))
                .collect(Collectors.joining("，"));
        return "基于 " + engineType.name() + " 引擎的证据奖惩和市场反馈，生成候选规则调整；因子核心变化："
                + (top.isBlank() ? "暂无显著因子权重变化" : top)
                + "；组合经验：" + (combinations.isBlank() ? "暂无组合调整" : combinations);
    }

    private String riskSummary(int minSampleCount, BigDecimal maxAbsDelta, List<Map<String, Object>> combinationStats,
                               List<String> growthHighlights) {
        String disabledCombinations = actionableCombinationStats(combinationStats).stream()
                .filter(row -> "DISABLE".equalsIgnoreCase(text(row, "suggested_action")))
                .map(row -> text(row, "combination_code") + "(命中率=" + decimal(row, "hit_rate")
                        + "，回撤=" + decimal(row, "avg_drawdown") + ")")
                .collect(Collectors.joining("，"));
        String growthText = growthHighlights.isEmpty() ? "暂无额外成长日志" : String.join("；", growthHighlights);
        return "候选版本仍需人工审批；仅采用样本数 >= " + minSampleCount
                + " 的经验统计，单因子单次最大调整限制为 " + scale(maxAbsDelta)
                + "，避免少量样本导致规则漂移。组合风险："
                + (disabledCombinations.isBlank() ? "暂无需禁用组合" : disabledCombinations)
                + "。成长日志：" + growthText;
    }

    private boolean disableCombination(RuleVersionCandidateEntity candidate, String combinationCode) {
        return combinationStats(candidate.getStatDate(), candidate.getBaseRuleVersion(),
                enumValue(EngineType.class, candidate.getEngineType()), 1).stream()
                .anyMatch(row -> combinationCode.equals(text(row, "combination_code"))
                        && "DISABLE".equalsIgnoreCase(text(row, "suggested_action")));
    }

    private List<Map<String, Object>> combinationStats(LocalDate statDate, String ruleVersion, EngineType engineType, int minSampleCount) {
        String sql = """
                select combination_code, sample_count, hit_count, miss_count, hit_rate,
                       avg_forward_return, avg_drawdown, suggested_action
                from factor_combination_performance_stat
                where stat_date = ? and rule_version = ? and engine_type = ? and sample_count >= ?
                order by case suggested_action
                           when 'DISABLE' then 1
                           when 'DECREASE_WEIGHT' then 2
                           when 'INCREASE_WEIGHT' then 3
                           else 4
                         end,
                         sample_count desc
                """;
        return jdbcTemplate.queryForList(sql, statDate, ruleVersion, engineType.name(), minSampleCount);
    }

    private List<Map<String, Object>> actionableCombinationStats(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(row -> {
                    String action = text(row, "suggested_action");
                    return StringUtils.hasText(action) && !"KEEP".equalsIgnoreCase(action);
                })
                .toList();
    }

    private List<String> growthHighlights(LocalDate statDate, EngineType engineType) {
        String sql = """
                select title, content
                from system_growth_log
                where trade_date = ? and (engine_type = ? or engine_type is null)
                order by id desc
                limit 5
                """;
        return jdbcTemplate.queryForList(sql, statDate, engineType.name()).stream()
                .map(row -> text(row, "title") + ":" + text(row, "content"))
                .toList();
    }

    private String changeReason(RuleVersionCandidateFactorEntity entity, Map<String, Object> row) {
        return "样本数 " + entity.getSampleCount()
                + "，命中率 " + entity.getHitRate()
                + "，平均贡献 " + entity.getAvgContributionScore()
                + "，冲突证据命中 " + integer(row, "conflict_hit_count")
                + "；根据明日验证、人工修正和证据奖惩，建议 "
                + entity.getSuggestedAction() + "，权重从 "
                + entity.getCurrentWeight() + " 调整到 " + entity.getSuggestedWeight() + "。";
    }

    private String action(BigDecimal delta, int sampleCount) {
        if (sampleCount < 3) {
            return "NEED_MANUAL_REVIEW";
        }
        if (delta.compareTo(ZERO) > 0) {
            return "INCREASE_WEIGHT";
        }
        if (delta.compareTo(ZERO) < 0) {
            return "DECREASE_WEIGHT";
        }
        return "KEEP";
    }

    private void growth(LocalDate tradeDate, String growthType, String engineType, String title, String content,
                        String beforeValue, String afterValue, String sourceRef) {
        jdbcTemplate.update("""
                insert into system_growth_log
                    (growth_id, trade_date, growth_type, engine_type, title, content, before_value, after_value, source_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "GROWTH-" + UUID.randomUUID(),
                tradeDate == null ? LocalDate.now() : tradeDate,
                growthType,
                engineType,
                title,
                content,
                beforeValue,
                afterValue,
                sourceRef);
    }

    private List<EngineType> defaultEngines() {
        return EnumSet.of(EngineType.CYCLE, EngineType.MAINLINE, EngineType.LEADER,
                EngineType.DIVERGENCE_CONSENSUS, EngineType.RISK).stream().toList();
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private String sign(BigDecimal value) {
        return value.compareTo(ZERO) >= 0 ? "+" : "-";
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
            return scale(decimal);
        }
        if (value instanceof Number number) {
            return scale(BigDecimal.valueOf(number.doubleValue()));
        }
        return value == null ? ZERO : scale(new BigDecimal(String.valueOf(value)));
    }

    private String safe(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        return Enum.valueOf(enumType, value);
    }
}
