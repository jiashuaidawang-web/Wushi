package com.wushi.common.model;

import com.wushi.common.enums.EngineType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class RuleContext {

    private String ruleVersion;
    private EngineType engineType;
    private Map<String, BigDecimal> factorWeights;
    private Map<String, BigDecimal> thresholdValues;
    private Map<String, String> thresholdOperators;
    private Map<String, String> evidenceTypes;
    private List<String> combinationCodes;
    private Map<String, Object> parameters;
}
