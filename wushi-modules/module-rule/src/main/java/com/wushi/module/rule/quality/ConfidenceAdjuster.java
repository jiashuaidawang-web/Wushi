package com.wushi.module.rule.quality;

import com.wushi.common.model.DataQualityContext;

import java.math.BigDecimal;

public interface ConfidenceAdjuster {

    BigDecimal applyDataQualityPenalty(BigDecimal baseConfidence, DataQualityContext dataQualityContext);

    BigDecimal clamp(BigDecimal confidence);
}
