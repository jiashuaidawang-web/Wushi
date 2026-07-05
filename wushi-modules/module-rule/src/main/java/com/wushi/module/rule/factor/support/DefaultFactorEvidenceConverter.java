package com.wushi.module.rule.factor.support;

import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.RuleContext;
import org.springframework.stereotype.Component;

@Component
public class DefaultFactorEvidenceConverter implements FactorEvidenceConverter {

    @Override
    public EvidenceItem toEvidence(FactorResult factorResult, RuleContext ruleContext) {
        String ruleVersion = ruleContext == null ? null : ruleContext.getRuleVersion();
        return EvidenceItem.builder()
                .evidenceCode(factorResult.getFactorCode())
                .evidenceType(factorResult.getEvidenceType())
                .title(factorResult.getFactorName())
                .description(factorResult.getDescription())
                .score(factorResult.getScore())
                .weight(factorResult.getWeight())
                .sourceTable(factorResult.getSourceTable())
                .sourceKey(factorResult.getSourceKey())
                .ruleVersion(ruleVersion)
                .validationStatus("PENDING")
                .build();
    }
}
