package com.wushi.module.rule.factor;

import com.wushi.common.enums.EvidenceType;
import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.RuleContext;
import com.wushi.module.rule.factor.support.FactorEvidenceConverter;
import com.wushi.module.rule.factor.support.ThresholdMatcher;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public abstract class AbstractFactorCalculator implements FactorCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private final ThresholdMatcher thresholdMatcher;
    private final FactorEvidenceConverter evidenceConverter;

    protected FactorResult buildFactor(
            FactorCalculateRequest request,
            String factorCode,
            String factorName,
            BigDecimal factorValue,
            String sourceTable,
            String sourceKey,
            String description
    ) {
        RuleContext ruleContext = request.getRuleContext();
        BigDecimal thresholdValue = ruleContext == null ? null : getDecimal(ruleContext.getThresholdValues(), factorCode);
        String thresholdOperator = ruleContext == null ? null : getString(ruleContext.getThresholdOperators(), factorCode);
        BigDecimal weight = ruleContext == null ? null : getDecimal(ruleContext.getFactorWeights(), factorCode);
        boolean thresholdPassed = thresholdMatcher.matches(factorValue, thresholdValue, thresholdOperator);
        EvidenceType configuredType = resolveEvidenceType(ruleContext == null ? null : getString(ruleContext.getEvidenceTypes(), factorCode));
        String factorDirection = ruleContext == null ? null : getString(ruleContext.getFactorDirections(), factorCode);
        EvidenceType evidenceType = resolveFinalEvidenceType(configuredType, factorDirection, thresholdOperator, thresholdPassed);

        return FactorResult.builder()
                .factorCode(factorCode)
                .factorName(factorName)
                .factorValue(scale(factorValue))
                .thresholdValue(scale(thresholdValue))
                .thresholdOperator(thresholdOperator)
                .thresholdPassed(thresholdPassed)
                .score(thresholdPassed ? ONE : ZERO)
                .weight(scale(weight))
                .evidenceType(evidenceType)
                .sourceTable(sourceTable)
                .sourceKey(sourceKey)
                .description(description)
                .build();
    }

    protected FactorCalculateResult assemble(String calculatorCode, RuleContext ruleContext, List<FactorResult> factors) {
        List<EvidenceItem> evidenceList = new ArrayList<>();
        List<EvidenceItem> conflictList = new ArrayList<>();
        List<EvidenceItem> warningList = new ArrayList<>();

        for (FactorResult factor : factors) {
            EvidenceItem evidence = evidenceConverter.toEvidence(factor, ruleContext);
            if (factor.getEvidenceType() == EvidenceType.SUPPORT) {
                evidenceList.add(evidence);
            } else if (factor.getEvidenceType() == EvidenceType.WARNING) {
                warningList.add(evidence);
            } else if (factor.getEvidenceType() == EvidenceType.CONFLICT) {
                conflictList.add(evidence);
            }
        }

        return FactorCalculateResult.builder()
                .calculatorCode(calculatorCode)
                .factorResults(factors)
                .evidenceList(evidenceList)
                .conflictList(conflictList)
                .warningList(warningList)
                .build();
    }

    protected BigDecimal decimal(Map<String, Object> facts, String key) {
        if (facts == null || !facts.containsKey(key) || facts.get(key) == null) {
            return null;
        }
        Object value = facts.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal getDecimal(Map<String, BigDecimal> values, String key) {
        if (values == null) {
            return null;
        }
        return values.get(key);
    }

    private String getString(Map<String, String> values, String key) {
        if (values == null) {
            return null;
        }
        return values.get(key);
    }

    private EvidenceType resolveEvidenceType(String evidenceType) {
        if (evidenceType == null || evidenceType.isBlank()) {
            return EvidenceType.SUPPORT;
        }
        return EvidenceType.valueOf(evidenceType);
    }

    private EvidenceType resolveFinalEvidenceType(EvidenceType configuredType, String factorDirection, String thresholdOperator, boolean thresholdPassed) {
        if ("NEGATIVE".equals(factorDirection)) {
            return resolveNegativeEvidenceType(configuredType, thresholdOperator, thresholdPassed);
        }
        return thresholdPassed ? configuredType : downgradeEvidenceType(configuredType);
    }

    private EvidenceType resolveNegativeEvidenceType(EvidenceType configuredType, String thresholdOperator, boolean thresholdPassed) {
        boolean acceptableWhenPassed = "LTE".equals(thresholdOperator) || "LT".equals(thresholdOperator);
        if (acceptableWhenPassed) {
            return thresholdPassed ? EvidenceType.SUPPORT : configuredType;
        }
        return thresholdPassed ? configuredType : EvidenceType.SUPPORT;
    }

    private EvidenceType downgradeEvidenceType(EvidenceType configuredType) {
        if (configuredType == EvidenceType.SUPPORT) {
            return EvidenceType.CONFLICT;
        }
        return configuredType;
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
