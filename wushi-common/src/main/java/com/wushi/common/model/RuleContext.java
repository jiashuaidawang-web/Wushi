package com.wushi.common.model;

import com.wushi.common.enums.EngineType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class RuleContext {

    private String ruleVersion;
    private EngineType engineType;
    private Map<String, Object> parameters;
}
