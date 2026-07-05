package com.wushi.module.rule.engine.task;

import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;

public abstract class NoopEngineTask implements EngineTask {

    @Override
    public EngineStepResult execute(EngineRunContext context) {
        return EngineStepResult.success(stepName(), 0);
    }
}
