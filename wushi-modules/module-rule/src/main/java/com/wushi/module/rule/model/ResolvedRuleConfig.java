package com.wushi.module.rule.model;

import com.wushi.common.enums.EngineType;
import com.wushi.module.rule.domain.entity.FactorCombinationDefinitionEntity;
import com.wushi.module.rule.domain.entity.FactorDefinitionEntity;
import com.wushi.module.rule.domain.entity.RuleFactorWeightEntity;
import com.wushi.module.rule.domain.entity.RuleVersionEntity;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@Builder
public class ResolvedRuleConfig {

    private EngineType engineType;
    private String ruleVersion;
    private RuleVersionEntity rule;
    private List<FactorDefinitionEntity> factors;
    private List<RuleFactorWeightEntity> factorWeights;
    private List<FactorCombinationDefinitionEntity> combinations;
    private Map<String, FactorDefinitionEntity> factorMap;
    private Map<String, RuleFactorWeightEntity> weightMap;

    public Optional<RuleFactorWeightEntity> findWeight(String factorCode) {
        return Optional.ofNullable(weightMap == null ? null : weightMap.get(factorCode));
    }

    public Optional<FactorDefinitionEntity> findFactor(String factorCode) {
        return Optional.ofNullable(factorMap == null ? null : factorMap.get(factorCode));
    }
}
