package com.wushi.module.spider.audit.service;

import com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity;
import com.wushi.module.spider.job.SpiderJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderBatchOrchestratorService {

    private final SpiderAuditService auditService;
    private final SpiderJob spiderJob;
    private final com.wushi.module.spider.audit.mapper.SpiderTaskCheckpointMapper checkpointMapper;
    private final com.wushi.module.market.repository.MarketFactRepository marketFactRepository;

    private static final List<String> EAST_MONEY_TASKS = List.of(
            "stock_daily_kline", "index_daily_kline", "plate_dimension",
            "plate_daily_kline", "plate_relation", "limit_status_daily",
            "limit_intraday_event", "capital_flow"
    );

    private static final int[] BACKOFF_SECONDS = {60, 120, 300, 600, 1800, 3600, 7200};

    private static int getWaitSeconds(int round) {
        return round < BACKOFF_SECONDS.length ? BACKOFF_SECONDS[round] : BACKOFF_SECONDS[BACKOFF_SECONDS.length - 1];
    }

    public boolean runUntilComplete(LocalDate tradeDate) {
        String auditId = auditService.generateAuditId();
        LocalDateTime cutoffTime = tradeDate.atTime(23, 59, 59);

        Set<String> everSucceeded = new HashSet<>(findSucceededTasks(tradeDate));
        log.info("===== 跑批启动: tradeDate={}, everSucceeded={} =====", tradeDate, everSucceeded);

        for (String taskCode : EAST_MONEY_TASKS) {
            if (everSucceeded.contains(taskCode)) {
                log.info("  ↳ {} 已历史成功 → 跳过", taskCode);
                continue;
            }
            runSingleTask(taskCode, tradeDate, auditId);
        }

        List<SpiderTaskCheckpointEntity> failed = auditService.findFailedTasks(tradeDate).stream()
                .filter(t -> !everSucceeded.contains(t.getTaskCode()))
                .toList();
        if (failed.isEmpty()) {
            log.info("✅ 全部完成: tradeDate={}", tradeDate);
            return true;
        }

        for (int round = 0; round < BACKOFF_SECONDS.length; round++) {
            if (LocalDateTime.now().isAfter(cutoffTime)) break;
            int wait = getWaitSeconds(round);
            if (LocalDateTime.now().plusSeconds(wait).isAfter(cutoffTime)) break;

            log.info("===== 第{}轮重试: 等{}s, failed={} =====",
                    round + 2, wait, failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).toList());
            sleep(wait);

            List<String> failedCodes = failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).distinct().toList();
            for (String taskCode : failedCodes) {
                runSingleTask(taskCode, tradeDate, auditId);
            }

            failed = auditService.findFailedTasks(tradeDate).stream()
                    .filter(t -> !everSucceeded.contains(t.getTaskCode()))
                    .toList();
            if (failed.isEmpty()) {
                log.info("✅ 全部完成: tradeDate={}, 第{}轮成功", tradeDate, round + 2);
                return true;
            }
        }

        log.error("🚨 跑批失败: tradeDate={}, failed={}",
                tradeDate, failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).toList());
        return false;
    }

    /** 查询已成功的,并 touch updated_at */
    private List<String> findSucceededTasks(LocalDate tradeDate) {
        List<SpiderTaskCheckpointEntity> succeeded = auditService.findAllTasks(tradeDate).stream()
                .filter(t -> "SUCCESS".equals(t.getStatus()))
                .toList();
        for (SpiderTaskCheckpointEntity t : succeeded) {
            SpiderTaskCheckpointEntity touch = new SpiderTaskCheckpointEntity();
            touch.setUpdatedAt(LocalDateTime.now());
            checkpointMapper.update(touch,
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SpiderTaskCheckpointEntity>()
                            .eq(SpiderTaskCheckpointEntity::getId, t.getId()));
        }
        return succeeded.stream().map(SpiderTaskCheckpointEntity::getTaskCode).distinct().toList();
    }

    private void runSingleTask(String taskCode, LocalDate tradeDate, String auditId) {
        auditService.markRunning(taskCode, taskCode, tradeDate, "EAST_MONEY");
        try {
            int inserted = spiderJob.runEastMoneyByTaskCode(taskCode, tradeDate);
            
            // 金融级严谨:验证 ClickHouse 数据真实落库
            String chTable = resolveTargetTable(taskCode);
            int clickhouseCount = countClickHouseRows(chTable, tradeDate);
            
            if (clickhouseCount > 0) {
                // ✅ ClickHouse 有数据 → 真实成功
                auditService.markSuccess(taskCode, tradeDate, "EAST_MONEY", clickhouseCount, 0);
                auditService.logAudit(auditId, taskCode, tradeDate, "EAST_MONEY",
                        chTable, "SUCCESS", clickhouseCount, clickhouseCount, 0, null);
                log.info("  ↳ {} → SUCCESS (ClickHouse 验证: {} 行)", taskCode, clickhouseCount);
            } else {
                // ❌ ClickHouse 没数据 → 真实失败,触发重试
                throw new RuntimeException("ClickHouse 无数据: table=" + chTable + ", tradeDate=" + tradeDate);
            }
        } catch (Exception e) {
            auditService.markFailed(taskCode, tradeDate, "EAST_MONEY", e.getMessage());
            auditService.logAudit(auditId, taskCode, tradeDate, "EAST_MONEY",
                    resolveTargetTable(taskCode), "FAILED", 0, 0, 0, e.getMessage());
            log.warn("  ↳ {} → FAILED: {}", taskCode, e.getMessage());
        }
    }

    /** 查 ClickHouse 某表某日的真实行数 — 金融级对账 */
    private int countClickHouseRows(String tableName, LocalDate tradeDate) {
        return marketFactRepository.countByTradeDate(tableName, tradeDate);
    }

    private String resolveTargetTable(String taskCode) {
        return switch (taskCode) {
            case "stock_daily_kline" -> "stock_daily_kline";
            case "index_daily_kline" -> "index_daily_kline";
            case "plate_dimension" -> "stock_plate_dimension";
            case "plate_daily_kline" -> "stock_plate_daily_kline";
            case "plate_relation" -> "stock_plate_relation_snapshot";
            case "limit_status_daily" -> "stock_limit_status_daily";
            case "limit_intraday_event" -> "stock_limit_intraday_event";
            case "capital_flow" -> "capital_flow_daily_snapshot";
            default -> taskCode;
        };
    }

    private void sleep(long seconds) {
        try { Thread.sleep(seconds * 1000); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
