package com.wushi.module.rule.engine.core;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EngineStepResult {

    private String stepName;
    private boolean success;
    private long affectedRows;
    private String message;

    public static EngineStepResult success(String stepName, long affectedRows) {
        return EngineStepResult.builder()
                .stepName(stepName)
                .success(true)
                .affectedRows(affectedRows)
                .message("success")
                .build();
    }

    public static EngineStepResult failure(String stepName, String message) {
        return EngineStepResult.builder()
                .stepName(stepName)
                .success(false)
                .affectedRows(0)
                .message(message)
                .build();
    }
}
