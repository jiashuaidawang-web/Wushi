package com.wushi.module.rule.engine.core;

import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.RunMode;
import com.wushi.module.rule.model.ResolvedRuleConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class EngineRunContext {

    private String batchId;
    private LocalDate tradeDate;
    private LocalDate asOfDate;
    private JudgementMode judgementMode;
    private RunMode runMode;
    private String ruleVersion;
    private Map<EngineType, ResolvedRuleConfig> ruleConfigs;

    public void ensureBatchId() {
        if (batchId == null || batchId.isBlank()) {
            batchId = "BATCH-" + UUID.randomUUID();
        }
    }

    public Map<EngineType, ResolvedRuleConfig> getOrCreateRuleConfigs() {
        if (ruleConfigs == null) {
            ruleConfigs = new EnumMap<>(EngineType.class);
        }
        return ruleConfigs;
    }
}
