package com.wushi.module.rule.factor.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
public class DefaultThresholdMatcher implements ThresholdMatcher {

    @Override
    public boolean matches(BigDecimal actualValue, BigDecimal thresholdValue, String thresholdOperator) {
        if (actualValue == null || thresholdValue == null || !StringUtils.hasText(thresholdOperator)) {
            return false;
        }
        int compared = actualValue.compareTo(thresholdValue);
        return switch (thresholdOperator) {
            case "GT" -> compared > 0;
            case "GTE" -> compared >= 0;
            case "LT" -> compared < 0;
            case "LTE" -> compared <= 0;
            case "EQ" -> compared == 0;
            default -> false;
        };
    }
}
