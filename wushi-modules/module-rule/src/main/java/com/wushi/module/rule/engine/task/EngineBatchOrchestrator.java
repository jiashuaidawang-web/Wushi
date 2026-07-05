package com.wushi.module.rule.engine.task;

import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EngineBatchOrchestrator {

    private final List<EngineTask> tasks;

    public List<EngineStepResult> run(EngineRunContext context) {
        return tasks.stream()
                .sorted(Comparator.comparingInt(EngineTask::order))
                .map(task -> task.execute(context))
                .toList();
    }
}
