package com.wushi.module.rule.service.impl;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.RuleContext;
import com.wushi.module.rule.domain.entity.FactorCombinationDefinitionEntity;
import com.wushi.module.rule.domain.entity.RuleFactorWeightEntity;
import com.wushi.module.rule.model.ResolvedRuleConfig;
import com.wushi.module.rule.service.EngineRuleContextFactory;
import com.wushi.module.rule.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EngineRuleContextFactoryImpl implements EngineRuleContextFactory {

    private final RuleConfigService ruleConfigService;

    @Override
    public RuleContext create(EngineType engineType, String requestedRuleVersion) {
        return create(ruleConfigService.resolve(engineType, requestedRuleVersion));
    }

    @Override
    public RuleContext create(ResolvedRuleConfig resolvedRuleConfig) {
        Map<String, BigDecimal> factorWeights = new HashMap<>();
        Map<String, BigDecimal> thresholdValues = new HashMap<>();
        Map<String, String> thresholdOperators = new HashMap<>();
        Map<String, String> evidenceTypes = new HashMap<>();

        for (RuleFactorWeightEntity weight : resolvedRuleConfig.getFactorWeights()) {
            factorWeights.put(weight.getFactorCode(), weight.getWeight());
            thresholdValues.put(weight.getFactorCode(), weight.getThresholdValue());
            thresholdOperators.put(weight.getFactorCode(), weight.getThresholdOperator());
            evidenceTypes.put(weight.getFactorCode(), weight.getEvidenceType());
        }

        return RuleContext.builder()
                .engineType(resolvedRuleConfig.getEngineType())
                .ruleVersion(resolvedRuleConfig.getRuleVersion())
                .factorWeights(factorWeights)
                .thresholdValues(thresholdValues)
                .thresholdOperators(thresholdOperators)
                .evidenceTypes(evidenceTypes)
                .combinationCodes(resolvedRuleConfig.getCombinations().stream()
                        .map(FactorCombinationDefinitionEntity::getCombinationCode)
                        .toList())
                .parameters(resolvedRuleConfig.getCombinations().stream()
                        .collect(Collectors.toMap(
                                FactorCombinationDefinitionEntity::getCombinationCode,
                                FactorCombinationDefinitionEntity::getConditionExpression,
                                (left, right) -> left)))
                .build();
    }
}
