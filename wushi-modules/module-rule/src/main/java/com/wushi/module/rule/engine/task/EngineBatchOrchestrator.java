package com.wushi.module.rule.engine.task;

import com.wushi.common.enums.BatchStatus;
import com.wushi.common.enums.EngineType;
import com.wushi.common.enums.StepStatus;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.service.EngineBatchLogService;
import com.wushi.module.rule.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EngineBatchOrchestrator {

    private final List<EngineTask> tasks;
    private final EngineBatchLogService batchLogService;
    private final RuleConfigService ruleConfigService;

    public List<EngineStepResult> run(EngineRunContext context) {
        context.ensureBatchId();
        resolveRuleConfigs(context);
        batchLogService.startRun(context);

        List<EngineTask> orderedTasks = tasks.stream()
                .sorted(Comparator.comparingInt(EngineTask::order))
                .toList();
        Map<String, EngineTask> taskMap = orderedTasks.stream()
                .collect(Collectors.toMap(EngineTask::stepName, Function.identity()));
        List<EngineStepResult> results = new ArrayList<>();

        try {
            for (EngineTask task : orderedTasks) {
                validateDependencies(task, taskMap);
                if (!dependenciesSucceeded(context.getBatchId(), task)) {
                    batchLogService.skipStep(context.getBatchId(), task, "dependency not successful");
                    results.add(EngineStepResult.failure(task.stepName(), "dependency not successful"));
                    continue;
                }

                batchLogService.startStep(context.getBatchId(), task);
                EngineStepResult result;
                try {
                    result = task.execute(context);
                } catch (Exception exception) {
                    batchLogService.failStep(context.getBatchId(), task, exception);
                    throw exception;
                }
                batchLogService.finishStep(context.getBatchId(), task, result);
                results.add(result);

                if (!result.isSuccess() && !result.isShouldContinue()) {
                    batchLogService.finishRun(context.getBatchId(), BatchStatus.FAILED, result.getMessage());
                    return results;
                }
            }
            BatchStatus finalStatus = results.stream().allMatch(EngineStepResult::isSuccess)
                    ? BatchStatus.SUCCESS
                    : BatchStatus.PARTIAL;
            batchLogService.finishRun(context.getBatchId(), finalStatus, null);
            return results;
        } catch (Exception exception) {
            batchLogService.finishRun(context.getBatchId(), BatchStatus.FAILED, exception.getMessage());
            throw exception;
        }
    }

    private boolean dependenciesSucceeded(String batchId, EngineTask task) {
        return task.dependsOn().stream()
                .allMatch(stepName -> batchLogService.getStepStatus(batchId, stepName) == StepStatus.SUCCESS);
    }

    private void validateDependencies(EngineTask task, Map<String, EngineTask> taskMap) {
        for (String dependency : task.dependsOn()) {
            if (!taskMap.containsKey(dependency)) {
                throw new IllegalStateException("Unknown dependency " + dependency + " for task " + task.stepName());
            }
        }
    }

    private void resolveRuleConfigs(EngineRunContext context) {
        Map<EngineType, com.wushi.module.rule.model.ResolvedRuleConfig> ruleConfigs = context.getOrCreateRuleConfigs();
        for (EngineType engineType : EngineType.values()) {
            ruleConfigs.computeIfAbsent(engineType, key -> ruleConfigService.resolve(key, context.getRuleVersion()));
        }
    }
}
