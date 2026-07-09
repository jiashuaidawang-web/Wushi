package com.wushi.module.rule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPAGE;
import com.baomidou.mybatisplus.extension.plugins.Page;
import java.time.LocalDate;

import com.wushi.common.enums.BatchStatus;
import com.wushi.common.enums.StepStatus;
import com.wushi.module.rule.domain.entity.EngineBatchRunLogEntity;
import com.wushi.module.rule.domain.entity.EngineBatchStepLogEntity;
import com.wushi.module.rule.engine.core.EngineRunContext;
import com.wushi.module.rule.engine.core.EngineStepResult;
import com.wushi.module.rule.engine.task.EngineTask;
import com.wushi.module.rule.mapper.EngineBatchRunLogMapper;
import com.wushi.module.rule.mapper.EngineBatchStepLogMapper;
import com.wushi.module.rule.service.EngineBatchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EngineBatchLogServiceImpl implements EngineBatchLogService {

    private final EngineBatchRunLogMapper runLogMapper;
    private final EngineBatchStepLogMapper stepLogMapper;

    @Override
    public void startRun(EngineRunContext context) {
        EngineBatchRunLogEntity entity = findRun(context.getBatchId());
        entity.setBatchId(context.getBatchId());
        entity.setTradeDate(context.getTradeDate());
        entity.setAsOfDate(context.getAsOfDate());
        entity.setJudgementMode(context.getJudgementMode().name());
        entity.setRunMode(context.getRunMode().name());
        entity.setRuleVersion(context.getRuleVersion());
        entity.setStatus(BatchStatus.RUNNING.name());
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(null);
        entity.setErrorMessage(null);
        if (entity.getId() == null) {
            runLogMapper.insert(entity);
        } else {
            runLogMapper.updateById(entity);
        }
    }

    @Override
    public void finishRun(String batchId, BatchStatus status, String errorMessage) {
        EngineBatchRunLogEntity entity = findRun(batchId);
        entity.setStatus(status.name());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(errorMessage);
        runLogMapper.updateById(entity);
    }

    @Override
    public void startStep(String batchId, EngineTask task) {
        EngineBatchStepLogEntity entity = findStep(batchId, task.stepName());
        entity.setBatchId(batchId);
        entity.setStepName(task.stepName());
        entity.setStepOrder(task.order());
        entity.setStatus(StepStatus.RUNNING.name());
        entity.setAffectedRows(0L);
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(null);
        entity.setErrorMessage(null);
        if (entity.getId() == null) {
            stepLogMapper.insert(entity);
        } else {
            stepLogMapper.updateById(entity);
        }
    }

    @Override
    public void finishStep(String batchId, EngineTask task, EngineStepResult result) {
        EngineBatchStepLogEntity entity = findStep(batchId, task.stepName());
        entity.setStatus(result.isSuccess() ? StepStatus.SUCCESS.name() : StepStatus.FAILED.name());
        entity.setAffectedRows(result.getAffectedRows());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(result.isSuccess() ? null : result.getMessage());
        stepLogMapper.updateById(entity);
    }

    @Override
    public void failStep(String batchId, EngineTask task, Exception exception) {
        EngineBatchStepLogEntity entity = findStep(batchId, task.stepName());
        entity.setStatus(StepStatus.FAILED.name());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(exception.getMessage());
        stepLogMapper.updateById(entity);
    }

    @Override
    public void skipStep(String batchId, EngineTask task, String reason) {
        EngineBatchStepLogEntity entity = findStep(batchId, task.stepName());
        entity.setBatchId(batchId);
        entity.setStepName(task.stepName());
        entity.setStepOrder(task.order());
        entity.setStatus(StepStatus.SKIPPED.name());
        entity.setAffectedRows(0L);
        entity.setStartedAt(LocalDateTime.now());
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(reason);
        if (entity.getId() == null) {
            stepLogMapper.insert(entity);
        } else {
            stepLogMapper.updateById(entity);
        }
    }

    @Override
    public StepStatus getStepStatus(String batchId, String stepName) {
        EngineBatchStepLogEntity entity = stepLogMapper.selectOne(new LambdaQueryWrapper<EngineBatchStepLogEntity>()
                .eq(EngineBatchStepLogEntity::getBatchId, batchId)
                .eq(EngineBatchStepLogEntity::getStepName, stepName)
                .last("limit 1"));
        if (entity == null) {
            return StepStatus.PENDING;
        }
        return StepStatus.valueOf(entity.getStatus());
    }

    private EngineBatchRunLogEntity findRun(String batchId) {
        EngineBatchRunLogEntity entity = runLogMapper.selectOne(new LambdaQueryWrapper<EngineBatchRunLogEntity>()
                .eq(EngineBatchRunLogEntity::getBatchId, batchId)
                .last("limit 1"));
        return entity == null ? new EngineBatchRunLogEntity() : entity;
    }

    private EngineBatchStepLogEntity findStep(String batchId, String stepName) {
        EngineBatchStepLogEntity entity = stepLogMapper.selectOne(new LambdaQueryWrapper<EngineBatchStepLogEntity>()
                .eq(EngineBatchStepLogEntity::getBatchId, batchId)
                .eq(EngineBatchStepLogEntity::getStepName, stepName)
                .last("limit 1"));
        return entity == null ? new EngineBatchStepLogEntity() : entity;
    }

    @Override
    public IPage<EngineBatchRunLogEntity> listHistory(LocalDate tradeDate, String status, int page, int size) {
        Page<EngineBatchRunLogEntity> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<EngineBatchRunLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (tradeDate != null) {
            wrapper.eq(EngineBatchRunLogEntity::getTradeDate, tradeDate);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EngineBatchRunLogEntity::getStatus, status);
        }
        wrapper.orderByDesc(EngineBatchRunLogEntity::getCreatedAt);
        return runLogMapper.selectPage(pageParam, wrapper);
    }

}
