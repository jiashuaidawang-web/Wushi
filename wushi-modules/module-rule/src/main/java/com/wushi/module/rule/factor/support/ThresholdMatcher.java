package com.wushi.module.rule.factor.support;

import java.math.BigDecimal;

public interface ThresholdMatcher {

    boolean matches(BigDecimal actualValue, BigDecimal thresholdValue, String thresholdOperator);
}
