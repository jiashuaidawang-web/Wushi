package com.wushi.module.rule.factor;

import com.wushi.common.enums.EngineType;
import com.wushi.common.model.RuleContext;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class FactorCalculateRequest {

    private LocalDate tradeDate;
    private LocalDate asOfDate;
    private EngineType engineType;
    private String targetCode;
    private String targetName;
    private RuleContext ruleContext;
    private Map<String, Object> facts;
    private Map<String, Object> params;
}
