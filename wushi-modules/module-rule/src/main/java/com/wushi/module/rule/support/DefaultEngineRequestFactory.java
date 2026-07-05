package com.wushi.module.rule.support;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.TargetType;
import com.wushi.common.model.DataQualityContext;
import com.wushi.common.model.RuleContext;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.quality.DataQualityAssessor;
import com.wushi.module.rule.service.EngineRuleContextFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DefaultEngineRequestFactory implements EngineRequestFactory {

    private final EngineRuleContextFactory engineRuleContextFactory;
    private final DataQualityAssessor dataQualityAssessor;

    @Override
    public EngineRequest create(
            LocalDate tradeDate,
            LocalDate asOfDate,
            JudgementMode judgementMode,
            EngineType engineType,
            TargetType targetType,
            String targetCode,
            String targetName,
            String requestedRuleVersion,
            Collection<String> requiredTables,
            Map<String, Object> params
    ) {
        RuleContext ruleContext = engineRuleContextFactory.create(engineType, requestedRuleVersion);
        DataQualityContext dataQualityContext = dataQualityAssessor.assess(tradeDate, requiredTables);
        return new EngineRequest(
                tradeDate,
                asOfDate,
                judgementMode,
                engineType,
                targetType,
                targetCode,
                targetName,
                ruleContext.getRuleVersion(),
                ruleContext,
                dataQualityContext,
                params == null ? Map.of() : params
        );
    }
}
