package com.wushi.module.rule.engine.core;

import com.wushi.common.enums.JudgementMode;
import com.wushi.common.enums.RunMode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
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

    public void ensureBatchId() {
        if (batchId == null || batchId.isBlank()) {
            batchId = "BATCH-" + UUID.randomUUID();
        }
    }
}
