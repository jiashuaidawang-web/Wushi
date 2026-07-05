package com.wushi.module.rule.engine.task;

import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;

import java.util.List;

public interface EngineTask {

    String stepName();

    int order();

    default List<String> dependsOn() {
        return List.of();
    }

    EngineStepResult execute(EngineRunContext context);
}
