package com.wushi.module.rule.service;

import com.wushi.common.enums.BatchStatus;
import com.wushi.common.enums.StepStatus;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineTask;

public interface EngineBatchLogService {

    void startRun(EngineRunContext context);

    void finishRun(String batchId, BatchStatus status, String errorMessage);

    void startStep(String batchId, EngineTask task);

    void finishStep(String batchId, EngineTask task, EngineStepResult result);

    void failStep(String batchId, EngineTask task, Exception exception);

    void skipStep(String batchId, EngineTask task, String reason);

    StepStatus getStepStatus(String batchId, String stepName);
}
