package com.wushi.module.rule.factor;

import java.util.List;

public interface FactorCalculator {

    String calculatorCode();

    List<String> supportFactorCodes();

    FactorCalculateResult calculate(FactorCalculateRequest request);
}
