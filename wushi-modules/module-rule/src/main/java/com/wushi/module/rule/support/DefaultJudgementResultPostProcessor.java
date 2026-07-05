package com.wushi.module.rule.support;

import com.wushi.common.model.DataQualityContext;
import com.wushi.common.model.JudgementResult;
import com.wushi.common.model.RuleContext;
import com.wushi.module.rule.engine.core.EngineRequest;
import com.wushi.module.rule.quality.ConfidenceAdjuster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DefaultJudgementResultPostProcessor implements JudgementResultPostProcessor {

    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.5000");

    private final ConfidenceAdjuster confidenceAdjuster;

    @Override
    public <T> JudgementResult<T> apply(EngineRequest request, JudgementResult<T> result) {
        if (result == null) {
            return null;
        }
        RuleContext ruleContext = request == null ? null : request.ruleContext();
        DataQualityContext dataQualityContext = result.getDataQualityContext() != null
                ? result.getDataQualityContext()
                : request == null ? null : request.dataQualityContext();

        String ruleVersion = firstText(result.getRuleVersion(),
                ruleContext == null ? null : ruleContext.getRuleVersion(),
                request == null ? null : request.ruleVersion());

        result.setRuleVersion(ruleVersion);
        result.setDataQualityContext(dataQualityContext);
        if (dataQualityContext != null && result.getDataQualityLevel() == null) {
            result.setDataQualityLevel(dataQualityContext.getLevel());
        }

        BigDecimal baseConfidence = result.getConfidence() == null ? DEFAULT_CONFIDENCE : result.getConfidence();
        result.setConfidence(confidenceAdjuster.applyDataQualityPenalty(baseConfidence, dataQualityContext));
        return result;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
