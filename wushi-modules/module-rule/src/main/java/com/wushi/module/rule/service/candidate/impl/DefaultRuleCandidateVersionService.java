package com.wushi.module.rule.service.candidate.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wushi.common.enums.EngineType;
import com.wushi.common.exception.BusinessException;
import com.wushi.module.rule.domain.entity.FactorCombinationDefinitionEntity;
import com.wushi.module.rule.domain.entity.RuleFactorWeightEntity;
import com.wushi.module.rule.domain.entity.RuleVersionEntity;
import com.wushi.module.rule.mapper.FactorCombinationDefinitionMapper;
import com.wushi.module.rule.mapper.RuleFactorWeightMapper;
import com.wushi.module.rule.mapper.RuleVersionMapper;
import com.wushi.module.rule.model.candidate.RuleCandidateApproveRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateFactorChange;
import com.wushi.module.rule.model.candidate.RuleCandidateGenerateRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateRejectRequest;
import com.wushi.module.rule.model.candidate.RuleCandidateVersion;
import com.wushi.module.rule.service.RuleConfigService;
import com.wushi.module.rule.service.candidate.RuleCandidateVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultRuleCandidateVersionService implements RuleCandidateVersionService {

    private static final String ACTIVE = "ACTIVE";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String CANDIDATE = "CANDIDATE";
    private static final String REJECTED = "REJECTED";
    private static final int MIN_SAMPLE_COUNT = 3;
    private static final DateTimeFormatter VERSION_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RuleVersionMapper ruleVersionMapper;
    private final RuleFactorWeightMapper ruleFactorWeightMapper;
    private final FactorCombinationDefinitionMapper factorCombinationDefinitionMapper;
    private final RuleConfigService ruleConfigService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public RuleCandidateVersion generateCandidate(RuleCandidateGenerateRequest request) {
        EngineType engineType = requireEngineType(request.engineType());
        LocalDate statDate = requireDate(request.statDate());
        String sourceRuleVersion = StringUtils.hasText(request.sourceRuleVersion())
                ? request.sourceRuleVersion()
                : ruleConfigService.findActiveRuleVersion(engineType)
                .orElseThrow(() -> new BusinessException("RULE_ACTIVE_NOT_FOUND", "未找到生效规则版本: " + engineType));
        RuleVersionEntity sourceRule = findRule(engineType, sourceRuleVersion);
        String candidateRuleVersion = StringUtils.hasText(request.candidateRuleVersion())
                ? request.candidateRuleVersion()
                : defaultCandidateVersion(sourceRuleVersion, statDate);
        assertRuleNotExists(engineType, candidateRuleVersion);

        List<RuleFactorWeightEntity> sourceWeights = listWeights(engineType, sourceRuleVersion);
        if (sourceWeights.isEmpty()) {
            throw new BusinessException("RULE_WEIGHT_EMPTY", "源规则没有可复制的因子权重: " + sourceRuleVersion);
        }
        Map<String, FactorStat> stats = loadActionableStats(statDate, sourceRuleVersion, engineType);
        if (stats.isEmpty()) {
            throw new BusinessException("RULE_CANDIDATE_NO_STATS", "没有可用于生成候选版本的经验统计");
        }

        int changedCount = 0;
        RuleVersionEntity candidate = new RuleVersionEntity();
        candidate.setRuleVersion(candidateRuleVersion);
        candidate.setRuleName(sourceRule.getRuleName() + " 候选 " + statDate);
        candidate.setEngineType(engineType.name());
        candidate.setStatus(CANDIDATE);
        candidate.setDescription(defaultText(request.description(), "由 " + statDate + " 经验统计从 " + sourceRuleVersion + " 生成"));
        candidate.setSourceRuleVersion(sourceRuleVersion);
        candidate.setCandidateStatDate(statDate);
        candidate.setCreatedBy(defaultText(request.createdBy(), "system"));
        ruleVersionMapper.insert(candidate);

        for (RuleFactorWeightEntity sourceWeight : sourceWeights) {
            FactorStat stat = stats.get(sourceWeight.getFactorCode());
            RuleFactorWeightEntity copied = copyWeight(sourceWeight, candidateRuleVersion);
            if (stat != null) {
                copied.setWeight(adjustWeight(sourceWeight.getWeight(), stat.suggestedWeightDelta()));
                changedCount++;
            }
            ruleFactorWeightMapper.insert(copied);
        }
        if (changedCount == 0) {
            throw new BusinessException("RULE_CANDIDATE_NO_MATCHED_FACTOR", "经验统计没有匹配到源规则中的因子权重");
        }
        copyCombinations(engineType, sourceRuleVersion, candidateRuleVersion);
        saveGrowthLog(statDate, engineType, "规则候选版本生成",
                "从 " + sourceRuleVersion + " 生成候选 " + candidateRuleVersion + "，调整因子 " + changedCount + " 个",
                sourceRuleVersion, candidateRuleVersion, candidateRuleVersion);
        return getCandidate(engineType, candidateRuleVersion);
    }

    @Override
    @Transactional
    public RuleCandidateVersion approveCandidate(RuleCandidateApproveRequest request) {
        EngineType engineType = requireEngineType(request.engineType());
        RuleVersionEntity candidate = findRule(engineType, requireRuleVersion(request.ruleVersion()));
        if (!CANDIDATE.equals(candidate.getStatus())) {
            throw new BusinessException("RULE_CANDIDATE_STATUS_INVALID", "只有 CANDIDATE 状态的规则版本可以批准");
        }
        LocalDate effectiveDate = request.effectiveDate() == null ? LocalDate.now() : request.effectiveDate();
        ruleVersionMapper.update(null, new LambdaUpdateWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineType.name())
                .eq(RuleVersionEntity::getStatus, ACTIVE)
                .set(RuleVersionEntity::getStatus, ARCHIVED));
        ruleVersionMapper.update(null, new LambdaUpdateWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineType.name())
                .eq(RuleVersionEntity::getRuleVersion, candidate.getRuleVersion())
                .set(RuleVersionEntity::getStatus, ACTIVE)
                .set(RuleVersionEntity::getEffectiveDate, effectiveDate)
                .set(RuleVersionEntity::getApprovedBy, defaultText(request.approvedBy(), "manual"))
                .set(RuleVersionEntity::getApprovedAt, LocalDateTime.now())
                .set(RuleVersionEntity::getApprovalRemark, request.approvalRemark()));
        saveGrowthLog(effectiveDate, engineType, "规则候选版本生效",
                "候选 " + candidate.getRuleVersion() + " 已批准并生效",
                candidate.getSourceRuleVersion(), candidate.getRuleVersion(), candidate.getRuleVersion());
        return getCandidate(engineType, candidate.getRuleVersion());
    }

    @Override
    @Transactional
    public RuleCandidateVersion rejectCandidate(RuleCandidateRejectRequest request) {
        EngineType engineType = requireEngineType(request.engineType());
        RuleVersionEntity candidate = findRule(engineType, requireRuleVersion(request.ruleVersion()));
        if (!CANDIDATE.equals(candidate.getStatus())) {
            throw new BusinessException("RULE_CANDIDATE_STATUS_INVALID", "只有 CANDIDATE 状态的规则版本可以拒绝");
        }
        ruleVersionMapper.update(null, new LambdaUpdateWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineType.name())
                .eq(RuleVersionEntity::getRuleVersion, candidate.getRuleVersion())
                .set(RuleVersionEntity::getStatus, REJECTED)
                .set(RuleVersionEntity::getApprovedBy, defaultText(request.rejectedBy(), "manual"))
                .set(RuleVersionEntity::getApprovedAt, LocalDateTime.now())
                .set(RuleVersionEntity::getApprovalRemark, request.rejectionReason()));
        saveGrowthLog(LocalDate.now(), engineType, "规则候选版本拒绝",
                "候选 " + candidate.getRuleVersion() + " 已拒绝：" + defaultText(request.rejectionReason(), "未填写原因"),
                candidate.getSourceRuleVersion(), candidate.getRuleVersion(), candidate.getRuleVersion());
        return getCandidate(engineType, candidate.getRuleVersion());
    }

    @Override
    public RuleCandidateVersion getCandidate(EngineType engineType, String ruleVersion) {
        RuleVersionEntity rule = findRule(requireEngineType(engineType), requireRuleVersion(ruleVersion));
        return toCandidateVersion(rule, true);
    }

    @Override
    public List<RuleCandidateVersion> listCandidates(EngineType engineType, String status) {
        LambdaQueryWrapper<RuleVersionEntity> wrapper = new LambdaQueryWrapper<RuleVersionEntity>()
                .isNotNull(RuleVersionEntity::getSourceRuleVersion)
                .orderByDesc(RuleVersionEntity::getCreatedAt)
                .orderByDesc(RuleVersionEntity::getId);
        if (engineType != null) {
            wrapper.eq(RuleVersionEntity::getEngineType, engineType.name());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(RuleVersionEntity::getStatus, status);
        }
        return ruleVersionMapper.selectList(wrapper).stream()
                .map(rule -> toCandidateVersion(rule, false))
                .toList();
    }

    private RuleCandidateVersion toCandidateVersion(RuleVersionEntity rule, boolean includeChanges) {
        List<RuleCandidateFactorChange> changes = includeChanges ? loadFactorChanges(rule) : List.of();
        return new RuleCandidateVersion(
                rule.getRuleVersion(),
                rule.getSourceRuleVersion(),
                enumValue(rule.getEngineType()),
                rule.getStatus(),
                rule.getCandidateStatDate(),
                rule.getEffectiveDate(),
                rule.getCreatedBy(),
                rule.getApprovedBy(),
                rule.getApprovedAt(),
                rule.getApprovalRemark(),
                rule.getDescription(),
                rule.getCreatedAt(),
                rule.getUpdatedAt(),
                changes
        );
    }

    private List<RuleCandidateFactorChange> loadFactorChanges(RuleVersionEntity rule) {
        if (!StringUtils.hasText(rule.getSourceRuleVersion())) {
            return List.of();
        }
        EngineType engineType = enumValue(rule.getEngineType());
        Map<String, RuleFactorWeightEntity> sourceWeights = listWeights(engineType, rule.getSourceRuleVersion()).stream()
                .collect(Collectors.toMap(RuleFactorWeightEntity::getFactorCode, Function.identity(), (left, right) -> left));
        Map<String, FactorStat> stats = rule.getCandidateStatDate() == null
                ? Map.of()
                : loadStats(rule.getCandidateStatDate(), rule.getSourceRuleVersion(), engineType);
        return listWeights(engineType, rule.getRuleVersion()).stream()
                .map(candidateWeight -> {
                    RuleFactorWeightEntity sourceWeight = sourceWeights.get(candidateWeight.getFactorCode());
                    FactorStat stat = stats.get(candidateWeight.getFactorCode());
                    return new RuleCandidateFactorChange(
                            candidateWeight.getFactorCode(),
                            sourceWeight == null ? null : sourceWeight.getWeight(),
                            candidateWeight.getWeight(),
                            stat == null ? BigDecimal.ZERO : stat.suggestedWeightDelta(),
                            stat == null ? 0 : stat.sampleCount(),
                            stat == null ? BigDecimal.ZERO : stat.hitRate(),
                            stat == null ? BigDecimal.ZERO : stat.avgContributionScore(),
                            candidateWeight.getEnabled()
                    );
                })
                .sorted(Comparator.comparing(RuleCandidateFactorChange::factorCode))
                .toList();
    }

    private RuleVersionEntity findRule(EngineType engineType, String ruleVersion) {
        RuleVersionEntity rule = ruleVersionMapper.selectOne(new LambdaQueryWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineType.name())
                .eq(RuleVersionEntity::getRuleVersion, ruleVersion)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException("RULE_VERSION_NOT_FOUND", "规则版本不存在: " + engineType + " " + ruleVersion);
        }
        return rule;
    }

    private void assertRuleNotExists(EngineType engineType, String ruleVersion) {
        Long count = ruleVersionMapper.selectCount(new LambdaQueryWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineType.name())
                .eq(RuleVersionEntity::getRuleVersion, ruleVersion));
        if (count != null && count > 0) {
            throw new BusinessException("RULE_VERSION_EXISTS", "规则版本已存在: " + engineType + " " + ruleVersion);
        }
    }

    private List<RuleFactorWeightEntity> listWeights(EngineType engineType, String ruleVersion) {
        return ruleFactorWeightMapper.selectList(new LambdaQueryWrapper<RuleFactorWeightEntity>()
                .eq(RuleFactorWeightEntity::getEngineType, engineType.name())
                .eq(RuleFactorWeightEntity::getRuleVersion, ruleVersion)
                .orderByAsc(RuleFactorWeightEntity::getFactorCode));
    }

    private Map<String, FactorStat> loadActionableStats(LocalDate statDate, String ruleVersion, EngineType engineType) {
        Map<String, FactorStat> stats = loadStats(statDate, ruleVersion, engineType);
        return stats.entrySet().stream()
                .filter(entry -> entry.getValue().sampleCount() >= MIN_SAMPLE_COUNT)
                .filter(entry -> entry.getValue().suggestedWeightDelta().compareTo(BigDecimal.ZERO) != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, FactorStat> loadStats(LocalDate statDate, String ruleVersion, EngineType engineType) {
        String sql = """
                select factor_code, sample_count, hit_rate, avg_contribution_score, suggested_weight_delta
                from factor_performance_stat
                where stat_date = ? and rule_version = ? and engine_type = ?
                """;
        Map<String, FactorStat> stats = new HashMap<>();
        jdbcTemplate.queryForList(sql, statDate, ruleVersion, engineType.name()).forEach(row -> {
            String factorCode = text(row.get("factor_code"));
            if (StringUtils.hasText(factorCode)) {
                stats.put(factorCode, new FactorStat(
                        factorCode,
                        integer(row.get("sample_count")),
                        decimal(row.get("hit_rate")),
                        decimal(row.get("avg_contribution_score")),
                        decimal(row.get("suggested_weight_delta"))
                ));
            }
        });
        return stats;
    }

    private RuleFactorWeightEntity copyWeight(RuleFactorWeightEntity source, String candidateRuleVersion) {
        RuleFactorWeightEntity copied = new RuleFactorWeightEntity();
        BeanUtils.copyProperties(source, copied);
        copied.setId(null);
        copied.setRuleVersion(candidateRuleVersion);
        copied.setCreatedAt(null);
        copied.setUpdatedAt(null);
        return copied;
    }

    private void copyCombinations(EngineType engineType, String sourceRuleVersion, String candidateRuleVersion) {
        List<FactorCombinationDefinitionEntity> combinations = factorCombinationDefinitionMapper.selectList(
                new LambdaQueryWrapper<FactorCombinationDefinitionEntity>()
                        .eq(FactorCombinationDefinitionEntity::getEngineType, engineType.name())
                        .eq(FactorCombinationDefinitionEntity::getRuleVersion, sourceRuleVersion));
        for (FactorCombinationDefinitionEntity source : combinations) {
            FactorCombinationDefinitionEntity copied = new FactorCombinationDefinitionEntity();
            BeanUtils.copyProperties(source, copied);
            copied.setId(null);
            copied.setRuleVersion(candidateRuleVersion);
            copied.setCreatedAt(null);
            copied.setUpdatedAt(null);
            factorCombinationDefinitionMapper.insert(copied);
        }
    }

    private BigDecimal adjustWeight(BigDecimal sourceWeight, BigDecimal delta) {
        BigDecimal adjusted = sourceWeight.add(delta);
        if (adjusted.compareTo(BigDecimal.ZERO) < 0) {
            adjusted = BigDecimal.ZERO;
        }
        if (adjusted.compareTo(BigDecimal.ONE) > 0) {
            adjusted = BigDecimal.ONE;
        }
        return adjusted.setScale(4, RoundingMode.HALF_UP);
    }

    private void saveGrowthLog(LocalDate tradeDate, EngineType engineType, String title, String content,
                               String beforeValue, String afterValue, String sourceRef) {
        String sql = """
                insert into system_growth_log
                (growth_id, trade_date, growth_type, engine_type, title, content, before_value, after_value, source_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, "GROWTH-" + UUID.randomUUID(), tradeDate, "RULE", engineType.name(),
                title, content, beforeValue, afterValue, sourceRef);
    }

    private EngineType requireEngineType(EngineType engineType) {
        if (engineType == null) {
            throw new BusinessException("RULE_ENGINE_REQUIRED", "engineType 不能为空");
        }
        return engineType;
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new BusinessException("RULE_STAT_DATE_REQUIRED", "statDate 不能为空");
        }
        return date;
    }

    private String requireRuleVersion(String ruleVersion) {
        if (!StringUtils.hasText(ruleVersion)) {
            throw new BusinessException("RULE_VERSION_REQUIRED", "ruleVersion 不能为空");
        }
        return ruleVersion;
    }

    private String defaultCandidateVersion(String sourceRuleVersion, LocalDate statDate) {
        return sourceRuleVersion + "-CAND-" + VERSION_DATE.format(statDate);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Object value) {
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

    private EngineType enumValue(String value) {
        return EngineType.valueOf(value);
    }

    private record FactorStat(
            String factorCode,
            int sampleCount,
            BigDecimal hitRate,
            BigDecimal avgContributionScore,
            BigDecimal suggestedWeightDelta
    ) {
    }
}
