package com.wushi.module.rule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wushi.common.enums.EngineType;
import com.wushi.module.rule.domain.entity.DashboardCardConfigEntity;
import com.wushi.module.rule.domain.entity.DataQualityImpactConfigEntity;
import com.wushi.module.rule.domain.entity.FactorCombinationDefinitionEntity;
import com.wushi.module.rule.domain.entity.FactorDefinitionEntity;
import com.wushi.module.rule.domain.entity.RuleFactorWeightEntity;
import com.wushi.module.rule.domain.entity.RuleVersionEntity;
import com.wushi.module.rule.mapper.DashboardCardConfigMapper;
import com.wushi.module.rule.mapper.DataQualityImpactConfigMapper;
import com.wushi.module.rule.mapper.FactorCombinationDefinitionMapper;
import com.wushi.module.rule.mapper.FactorDefinitionMapper;
import com.wushi.module.rule.mapper.RuleFactorWeightMapper;
import com.wushi.module.rule.mapper.RuleVersionMapper;
import com.wushi.module.rule.model.DashboardCardConfig;
import com.wushi.module.rule.model.ResolvedRuleConfig;
import com.wushi.module.rule.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RuleConfigServiceImpl implements RuleConfigService {

    private static final String ACTIVE = "ACTIVE";

    private final RuleVersionMapper ruleVersionMapper;
    private final FactorDefinitionMapper factorDefinitionMapper;
    private final RuleFactorWeightMapper ruleFactorWeightMapper;
    private final FactorCombinationDefinitionMapper factorCombinationDefinitionMapper;
    private final DataQualityImpactConfigMapper dataQualityImpactConfigMapper;
    private final DashboardCardConfigMapper dashboardCardConfigMapper;

    @Override
    public ResolvedRuleConfig resolve(EngineType engineType, String requestedRuleVersion) {
        String engineTypeName = engineType.name();
        String ruleVersion = StringUtils.hasText(requestedRuleVersion)
                ? requestedRuleVersion
                : findActiveRuleVersion(engineType).orElseThrow(() ->
                new IllegalStateException("No active rule version for engine " + engineTypeName));

        RuleVersionEntity rule = ruleVersionMapper.selectOne(new LambdaQueryWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineTypeName)
                .eq(RuleVersionEntity::getRuleVersion, ruleVersion)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new IllegalStateException("Rule version not found: " + engineTypeName + " " + ruleVersion);
        }

        List<FactorDefinitionEntity> factors = factorDefinitionMapper.selectList(new LambdaQueryWrapper<FactorDefinitionEntity>()
                .eq(FactorDefinitionEntity::getEngineType, engineTypeName)
                .eq(FactorDefinitionEntity::getEnabled, 1)
                .orderByAsc(FactorDefinitionEntity::getFactorCode));
        List<RuleFactorWeightEntity> weights = ruleFactorWeightMapper.selectList(new LambdaQueryWrapper<RuleFactorWeightEntity>()
                .eq(RuleFactorWeightEntity::getEngineType, engineTypeName)
                .eq(RuleFactorWeightEntity::getRuleVersion, ruleVersion)
                .eq(RuleFactorWeightEntity::getEnabled, 1)
                .orderByAsc(RuleFactorWeightEntity::getFactorCode));
        List<FactorCombinationDefinitionEntity> combinations = factorCombinationDefinitionMapper.selectList(new LambdaQueryWrapper<FactorCombinationDefinitionEntity>()
                .eq(FactorCombinationDefinitionEntity::getEngineType, engineTypeName)
                .eq(FactorCombinationDefinitionEntity::getRuleVersion, ruleVersion)
                .eq(FactorCombinationDefinitionEntity::getEnabled, 1)
                .orderByAsc(FactorCombinationDefinitionEntity::getCombinationCode));

        Map<String, FactorDefinitionEntity> factorMap = factors.stream()
                .collect(Collectors.toMap(FactorDefinitionEntity::getFactorCode, Function.identity(), (left, right) -> left));
        Map<String, RuleFactorWeightEntity> weightMap = weights.stream()
                .collect(Collectors.toMap(RuleFactorWeightEntity::getFactorCode, Function.identity(), (left, right) -> left));

        return ResolvedRuleConfig.builder()
                .engineType(engineType)
                .ruleVersion(ruleVersion)
                .rule(rule)
                .factors(factors)
                .factorWeights(weights)
                .combinations(combinations)
                .factorMap(factorMap)
                .weightMap(weightMap)
                .build();
    }

    @Override
    public Optional<String> findActiveRuleVersion(EngineType engineType) {
        RuleVersionEntity rule = ruleVersionMapper.selectOne(new LambdaQueryWrapper<RuleVersionEntity>()
                .eq(RuleVersionEntity::getEngineType, engineType.name())
                .eq(RuleVersionEntity::getStatus, ACTIVE)
                .orderByDesc(RuleVersionEntity::getEffectiveDate)
                .orderByDesc(RuleVersionEntity::getId)
                .last("LIMIT 1"));
        return Optional.ofNullable(rule).map(RuleVersionEntity::getRuleVersion);
    }

    @Override
    public List<DataQualityImpactConfigEntity> listDataQualityImpactConfigs() {
        return dataQualityImpactConfigMapper.selectList(new LambdaQueryWrapper<DataQualityImpactConfigEntity>()
                .eq(DataQualityImpactConfigEntity::getEnabled, 1)
                .orderByAsc(DataQualityImpactConfigEntity::getDataDomain)
                .orderByAsc(DataQualityImpactConfigEntity::getTableName));
    }

    @Override
    public List<DataQualityImpactConfigEntity> listDataQualityImpactConfigs(String tableName) {
        return dataQualityImpactConfigMapper.selectList(new LambdaQueryWrapper<DataQualityImpactConfigEntity>()
                .eq(DataQualityImpactConfigEntity::getEnabled, 1)
                .eq(DataQualityImpactConfigEntity::getTableName, tableName)
                .orderByAsc(DataQualityImpactConfigEntity::getDataDomain));
    }

    @Override
    public List<DashboardCardConfig> listDashboardCards(String pageCode) {
        return dashboardCardConfigMapper.selectList(new LambdaQueryWrapper<DashboardCardConfigEntity>()
                        .eq(DashboardCardConfigEntity::getEnabled, 1)
                        .eq(DashboardCardConfigEntity::getPageCode, pageCode)
                        .orderByAsc(DashboardCardConfigEntity::getDisplayOrder)
                        .orderByAsc(DashboardCardConfigEntity::getId))
                .stream()
                .map(this::toDashboardCardConfig)
                .toList();
    }

    private DashboardCardConfig toDashboardCardConfig(DashboardCardConfigEntity entity) {
        return DashboardCardConfig.builder()
                .pageCode(entity.getPageCode())
                .cardCode(entity.getCardCode())
                .cardName(entity.getCardName())
                .cardType(entity.getCardType())
                .dataApi(entity.getDataApi())
                .displayOrder(entity.getDisplayOrder())
                .requiredFields(entity.getRequiredFields())
                .thoughtMapping(entity.getThoughtMapping())
                .build();
    }
}
