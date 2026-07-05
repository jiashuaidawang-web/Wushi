package com.wushi.module.rule.service;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.RuleContext;
import com.wushi.module.rule.model.ResolvedRuleConfig;

public interface EngineRuleContextFactory {

    RuleContext create(EngineType engineType, String requestedRuleVersion);

    RuleContext create(ResolvedRuleConfig resolvedRuleConfig);
}
