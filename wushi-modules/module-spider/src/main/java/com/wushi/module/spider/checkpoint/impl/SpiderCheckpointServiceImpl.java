package com.wushi.module.spider.checkpoint.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wushi.module.spider.checkpoint.SpiderCheckpointService;
import com.wushi.module.spider.domain.SpiderTaskCheckpointEntity;
import com.wushi.module.spider.mapper.SpiderTaskCheckpointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderCheckpointServiceImpl implements SpiderCheckpointService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FAILED = "FAILED";

    private final SpiderTaskCheckpointMapper checkpointMapper;

    @Override
    public boolean isAlreadySucceeded(String taskCode, LocalDate tradeDate, String provider) {
        SpiderTaskCheckpointEntity latest = findLatest(taskCode, tradeDate, provider);
        return latest != null && STATUS_SUCCESS.equals(latest.getStatus());
    }

    @Override
    public void markRunning(String taskCode, String taskName, LocalDate tradeDate, String provider) {
        SpiderTaskCheckpointEntity entity = findLatest(taskCode, tradeDate, provider);
        if (entity == null) {
            entity = new SpiderTaskCheckpointEntity();
            entity.setTaskCode(taskCode);
            entity.setTaskName(taskName);
            entity.setTradeDate(tradeDate);
            entity.setProvider(provider);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setSuccessCount(0);
            entity.setFailCount(0);
        }
        entity.setStatus(STATUS_RUNNING);
        entity.setStartedAt(LocalDateTime.now());
        entity.setErrorMessage(null);
        entity.setUpdatedAt(LocalDateTime.now());
        saveOrUpdate(entity);
        log.info("任务标记为RUNNING: taskCode={}, tradeDate={}, provider={}", taskCode, tradeDate, provider);
    }

    @Override
    public void markSuccess(String taskCode, LocalDate tradeDate, String provider,
                            int successCount, int failCount, String checkpointValue) {
        SpiderTaskCheckpointEntity entity = findLatest(taskCode, tradeDate, provider);
        if (entity == null) {
            entity = new SpiderTaskCheckpointEntity();
            entity.setTaskCode(taskCode);
            entity.setTaskName(taskCode);
            entity.setTradeDate(tradeDate);
            entity.setProvider(provider);
            entity.setCreatedAt(LocalDateTime.now());
        }
        // 关键修复: success=0 不能算成功,标记为 FAILED 以便下次重试
        if (successCount <= 0) {
            entity.setStatus(STATUS_FAILED);
            entity.setErrorMessage("抓取数量为0，标记失败以便重试");
            log.warn("任务标记为FAILED(数据为空): taskCode={}, tradeDate={}, provider={}", taskCode, tradeDate, provider);
        } else {
            entity.setStatus(STATUS_SUCCESS);
            log.info("任务标记为SUCCESS: taskCode={}, tradeDate={}, provider={}, success={}, fail={}",
                    taskCode, tradeDate, provider, successCount, failCount);
        }
        entity.setSuccessCount(successCount);
        entity.setFailCount(failCount);
        entity.setCheckpointValue(checkpointValue);
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(null);
        entity.setUpdatedAt(LocalDateTime.now());
        saveOrUpdate(entity);
    }

    @Override
    public void markFailed(String taskCode, LocalDate tradeDate, String provider, String errorMessage) {
        SpiderTaskCheckpointEntity entity = findLatest(taskCode, tradeDate, provider);
        if (entity == null) {
            entity = new SpiderTaskCheckpointEntity();
            entity.setTaskCode(taskCode);
            entity.setTaskName(taskCode);
            entity.setTradeDate(tradeDate);
            entity.setProvider(provider);
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setStatus(STATUS_FAILED);
        entity.setFinishedAt(LocalDateTime.now());
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(LocalDateTime.now());
        saveOrUpdate(entity);
        log.warn("任务标记为FAILED: taskCode={}, tradeDate={}, provider={}, error={}",
                taskCode, tradeDate, provider, errorMessage);
    }

    private SpiderTaskCheckpointEntity findLatest(String taskCode, LocalDate tradeDate, String provider) {
        LambdaQueryWrapper<SpiderTaskCheckpointEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpiderTaskCheckpointEntity::getTaskCode, taskCode)
                .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                .eq(SpiderTaskCheckpointEntity::getProvider, provider)
                .orderByDesc(SpiderTaskCheckpointEntity::getId)
                .last("LIMIT 1");
        return checkpointMapper.selectOne(wrapper);
    }

    private void saveOrUpdate(SpiderTaskCheckpointEntity entity) {
        if (entity.getId() == null) {
            checkpointMapper.insert(entity);
        } else {
            checkpointMapper.updateById(entity);
        }
    }
}
