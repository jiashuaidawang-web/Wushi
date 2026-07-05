package com.wushi.module.rule.factor.support;

import com.wushi.common.model.EvidenceItem;
import com.wushi.common.model.FactorResult;
import com.wushi.common.model.RuleContext;

public interface FactorEvidenceConverter {

    EvidenceItem toEvidence(FactorResult factorResult, RuleContext ruleContext);
}
