package com.wushi.module.spider.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wushi.module.spider.audit.entity.DataSyncAuditLogEntity;
import com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity;
import com.wushi.module.spider.audit.mapper.DataSyncAuditLogMapper;
import com.wushi.module.spider.audit.mapper.SpiderTaskCheckpointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderAuditService {

    private final SpiderTaskCheckpointMapper checkpointMapper;
    private final DataSyncAuditLogMapper auditLogMapper;

    public String generateAuditId() {
        return "audit_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 记录任务开始 — UPSERT:冲突时重置状态+时间
     */
    public void markRunning(String taskCode, String taskName, LocalDate tradeDate, String provider) {
        SpiderTaskCheckpointEntity entity = findByTask(taskCode, tradeDate, provider);
        if (entity == null) {
            // 新记录
            entity = new SpiderTaskCheckpointEntity();
            entity.setTaskCode(taskCode);
            entity.setTaskName(taskName);
            entity.setTradeDate(tradeDate);
            entity.setProvider(provider);
            entity.setStatus("RUNNING");
            entity.setStartedAt(LocalDateTime.now());
            entity.setSuccessCount(null);
            entity.setFailCount(null);
            entity.setFinishedAt(null);
            entity.setErrorMessage(null);
            try {
                checkpointMapper.insert(entity);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发冲突,改为更新
                markRunningUpdate(taskCode, tradeDate, provider);
            }
        } else {
            markRunningUpdate(taskCode, tradeDate, provider);
        }
        log.info("checkpoint: taskCode={}, tradeDate={}, provider={} → RUNNING", taskCode, tradeDate, provider);
    }

    private void markRunningUpdate(String taskCode, LocalDate tradeDate, String provider) {
        SpiderTaskCheckpointEntity update = new SpiderTaskCheckpointEntity();
        update.setStatus("RUNNING");
        update.setStartedAt(LocalDateTime.now());
        update.setFinishedAt(null);
        update.setSuccessCount(null);
        update.setFailCount(null);
        update.setErrorMessage(null);
        checkpointMapper.update(update,
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTaskCode, taskCode)
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                        .eq(SpiderTaskCheckpointEntity::getProvider, provider)
        );
    }

    /**
     * 记录任务成功
     */
    public void markSuccess(String taskCode, LocalDate tradeDate, String provider,
                            int successCount, int failCount) {
        SpiderTaskCheckpointEntity update = new SpiderTaskCheckpointEntity();
        update.setStatus(failCount > 0 ? "PARTIAL" : "SUCCESS");
        update.setSuccessCount(successCount);
        update.setFailCount(failCount);
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(null);
        int updated = checkpointMapper.update(update,
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTaskCode, taskCode)
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                        .eq(SpiderTaskCheckpointEntity::getProvider, provider)
        );
        log.info("checkpoint: taskCode={}, tradeDate={} → {}, updated={}, success={}, fail={}",
                taskCode, tradeDate, update.getStatus(), updated, successCount, failCount);
    }

    /**
     * 记录任务失败
     */
    public void markFailed(String taskCode, LocalDate tradeDate, String provider, String errorMessage) {
        SpiderTaskCheckpointEntity update = new SpiderTaskCheckpointEntity();
        update.setStatus("FAILED");
        update.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) : errorMessage);
        update.setFinishedAt(LocalDateTime.now());
        int updated = checkpointMapper.update(update,
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTaskCode, taskCode)
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                        .eq(SpiderTaskCheckpointEntity::getProvider, provider)
        );
        log.warn("checkpoint: taskCode={}, tradeDate={} → FAILED, updated={}, err={}",
                taskCode, tradeDate, updated, errorMessage);
    }

    /**
     * 写审计日志
     */
    public void logAudit(String auditId, String taskCode, LocalDate tradeDate, String provider,
                         String targetTable, String syncStatus,
                         int fetchedCount, int insertedCount, int failedCount,
                         String errorMessage) {
        DataSyncAuditLogEntity entity = new DataSyncAuditLogEntity();
        entity.setAuditId(auditId);
        entity.setTaskCode(taskCode);
        entity.setTradeDate(tradeDate);
        entity.setProvider(provider);
        entity.setTargetTable(targetTable);
        entity.setSyncStatus(syncStatus);
        entity.setFetchedCount(fetchedCount);
        entity.setInsertedCount(insertedCount);
        entity.setUpdatedCount(0);
        entity.setFailedCount(failedCount);
        entity.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) : errorMessage);
        auditLogMapper.insert(entity);
        log.info("audit: taskCode={}, targetTable={}, syncStatus={}, fetched={}, inserted={}, failed={}",
                taskCode, targetTable, syncStatus, fetchedCount, insertedCount, failedCount);
    }

    /**
     * 查询失败任务
     */
    public List<SpiderTaskCheckpointEntity> findFailedTasks(LocalDate tradeDate) {
        return checkpointMapper.selectList(
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                        .ne(SpiderTaskCheckpointEntity::getStatus, "SUCCESS")
                        .ne(SpiderTaskCheckpointEntity::getStatus, "SKIPPED")
        );
    }

    /**
     * 查询所有任务
     */
    public List<SpiderTaskCheckpointEntity> findAllTasks(LocalDate tradeDate) {
        return checkpointMapper.selectList(
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                        .orderByAsc(SpiderTaskCheckpointEntity::getTaskCode)
        );
    }

    /**
     * 清空前一日所有 checkpoint + 审计日志 — 重跑前调用
     */
    public void clearCheckpoints(LocalDate tradeDate) {
        clearSpiderTaskCheckpoints(tradeDate);
        clearAuditLogs(tradeDate);
        log.info("清空 checkpoint + audit: tradeDate={}", tradeDate);
    }

    private void clearSpiderTaskCheckpoints(LocalDate tradeDate) {
        int deleted = checkpointMapper.delete(
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
        );
        log.info("清空 checkpoint: tradeDate={}, deleted={}", tradeDate, deleted);
    }

    private void clearAuditLogs(LocalDate tradeDate) {
        int deleted = auditLogMapper.delete(
                new LambdaQueryWrapper<DataSyncAuditLogEntity>()
                        .eq(DataSyncAuditLogEntity::getTradeDate, tradeDate)
        );
        log.info("清空 audit_log: tradeDate={}, deleted={}", tradeDate, deleted);
    }

    private SpiderTaskCheckpointEntity findByTask(String taskCode, LocalDate tradeDate, String provider) {
        return checkpointMapper.selectOne(
                new LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                        .eq(SpiderTaskCheckpointEntity::getTaskCode, taskCode)
                        .eq(SpiderTaskCheckpointEntity::getTradeDate, tradeDate)
                        .eq(SpiderTaskCheckpointEntity::getProvider, provider)
        );
    }
}
