package com.wushi.module.rule.quality.impl;

import com.wushi.common.model.DataQualityContext;
import com.wushi.module.rule.quality.ConfidenceAdjuster;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DefaultConfidenceAdjuster implements ConfidenceAdjuster {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    @Override
    public BigDecimal applyDataQualityPenalty(BigDecimal baseConfidence, DataQualityContext dataQualityContext) {
        BigDecimal confidence = baseConfidence == null ? ZERO : baseConfidence;
        BigDecimal penalty = dataQualityContext == null || dataQualityContext.getConfidencePenalty() == null
                ? ZERO
                : dataQualityContext.getConfidencePenalty();
        return clamp(confidence.subtract(penalty));
    }

    @Override
    public BigDecimal clamp(BigDecimal confidence) {
        if (confidence == null) {
            return ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (confidence.compareTo(ZERO) < 0) {
            return ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (confidence.compareTo(ONE) > 0) {
            return ONE.setScale(4, RoundingMode.HALF_UP);
        }
        return confidence.setScale(4, RoundingMode.HALF_UP);
    }
}
