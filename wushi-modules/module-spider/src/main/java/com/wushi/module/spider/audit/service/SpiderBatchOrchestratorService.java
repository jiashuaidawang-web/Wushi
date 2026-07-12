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

    private static final List<String> THS_TASKS = List.of(
            "ths_plate_dimension", "ths_plate_relation"
    );

    private static final int[] BACKOFF_SECONDS = {120, 300, 600, 1800, 3600};

    private static int getWaitSeconds(int round) {
        return round < BACKOFF_SECONDS.length ? BACKOFF_SECONDS[round] : BACKOFF_SECONDS[BACKOFF_SECONDS.length - 1];
    }

    /**
     * 跑 EAST_MONEY 全量(幂等 + 失败重试 + IP 轮换标记)
     */
    public boolean runUntilComplete(LocalDate tradeDate) {
        LocalDateTime cutoffTime = tradeDate.plusDays(1).atTime(23, 59, 59);
        LocalDateTime todayCutoff = LocalDate.now().atTime(23, 59, 59);
        if (todayCutoff.isAfter(cutoffTime)) cutoffTime = todayCutoff;

        Set<String> everSucceeded = new HashSet<>(findSucceededTasks(tradeDate));
        log.info("===== 跑批启动: tradeDate={}, everSucceeded={} =====", tradeDate, everSucceeded);

        for (String taskCode : EAST_MONEY_TASKS) {
            if (everSucceeded.contains(taskCode)) {
                log.info("  ↳ {} 已历史成功 → 跳过", taskCode);
                continue;
            }
            runSingleTask(taskCode, tradeDate);
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

            log.info("===== 第 {} 轮重试: tradeDate={}, 等待 {}s, failed={} =====",
                    round + 2, tradeDate, wait,
                    failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).toList());
            sleep(wait);

            List<String> failedCodes = failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).distinct().toList();
            for (String taskCode : failedCodes) {
                runSingleTask(taskCode, tradeDate);
            }

            failed = auditService.findFailedTasks(tradeDate).stream()
                    .filter(t -> !everSucceeded.contains(t.getTaskCode()))
                    .toList();
            if (failed.isEmpty()) {
                log.info("✅ 全部完成: tradeDate={}, 第{}轮成功", tradeDate, round + 2);
                return true;
            }
        }

        // 全都没成功 → 记一条特殊审计日志,提醒外部系统换 IP
        log.error("🚨 跑批彻底失败: tradeDate={}, failed={}, 请执行 modem_reboot_playwright.py 换 IP",
                tradeDate, failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).toList());
        auditService.logAudit("IP_ROTATION_NEEDED", tradeDate, "SYSTEM",
                "modem_reboot", "IP_ROTATION_NEEDED", 0, 0, 0,
                "Spider 连续失败次数达上限,需要换公网 IP 后重跑");
        return false;
    }

    /**
     * 跑 THS 全量(幂等 + 失败重试)
     */
    public boolean runThsUntilComplete(LocalDate tradeDate) {
        LocalDateTime cutoffTime = tradeDate.plusDays(1).atTime(23, 59, 59);
        LocalDateTime todayCutoff = LocalDate.now().atTime(23, 59, 59);
        if (todayCutoff.isAfter(cutoffTime)) cutoffTime = todayCutoff;

        Set<String> everSucceeded = new HashSet<>(findSucceededTasks(tradeDate));
        log.info("===== THS跑批启动: tradeDate={}, everSucceeded={} =====", tradeDate, everSucceeded);

        for (String taskCode : THS_TASKS) {
            if (everSucceeded.contains(taskCode)) {
                log.info("  ↳ {} 已历史成功 → 跳过", taskCode);
                continue;
            }
            runThsSingleTask(taskCode, tradeDate);
        }

        List<SpiderTaskCheckpointEntity> failed = auditService.findFailedTasks(tradeDate).stream()
                .filter(t -> !everSucceeded.contains(t.getTaskCode()))
                .toList();
        if (failed.isEmpty()) {
            log.info("✅ THS全部完成: tradeDate={}", tradeDate);
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
                runThsSingleTask(taskCode, tradeDate);
            }

            failed = auditService.findFailedTasks(tradeDate).stream()
                    .filter(t -> !everSucceeded.contains(t.getTaskCode()))
                    .toList();
            if (failed.isEmpty()) {
                log.info("✅ THS全部完成: tradeDate={}, 第{}轮成功", tradeDate, round + 2);
                return true;
            }
        }

        log.error("🚨 THS跑批失败: tradeDate={}, failed={}",
                tradeDate, failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).toList());
        return false;
    }

    /**
     * 查询已成功的,并 touch updated_at
     */
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

    private void runSingleTask(String taskCode, LocalDate tradeDate) {
        auditService.markRunning(taskCode, taskCode, tradeDate, "EAST_MONEY");
        try {
            int inserted = spiderJob.runEastMoneyByTaskCode(taskCode, tradeDate);

            // 金融级严谨:验证 ClickHouse 数据真实落库
            String chTable = resolveTargetTable(taskCode);
            int clickhouseCount = countClickHouseRows(chTable, tradeDate);

            if (clickhouseCount > 0) {
                auditService.markSuccess(taskCode, tradeDate, "EAST_MONEY", clickhouseCount, 0);
                auditService.logAudit(taskCode, tradeDate, "EAST_MONEY",
                        chTable, "SUCCESS", clickhouseCount, clickhouseCount, 0, null);
                log.info("  ↳ {} → SUCCESS (ClickHouse 验证: {} 行)", taskCode, clickhouseCount);
            } else {
                throw new RuntimeException("ClickHouse 无数据: table=" + chTable + ", tradeDate=" + tradeDate);
            }
        } catch (Exception e) {
            auditService.markFailed(taskCode, tradeDate, "EAST_MONEY", e.getMessage());
            auditService.logAudit(taskCode, tradeDate, "EAST_MONEY",
                    resolveTargetTable(taskCode), "FAILED", 0, 0, 0, e.getMessage());
            log.warn("  ↳ {} → FAILED: {}", taskCode, e.getMessage());
        }
    }

    private void runThsSingleTask(String taskCode, LocalDate tradeDate) {
        auditService.markRunning(taskCode, taskCode, tradeDate, "THS");
        try {
            int inserted = spiderJob.runThsByTaskCode(taskCode, tradeDate);

            String chTable = resolveThsTargetTable(taskCode);
            int clickhouseCount = countClickHouseRows(chTable, tradeDate);

            if (clickhouseCount > 0) {
                auditService.markSuccess(taskCode, tradeDate, "THS", clickhouseCount, 0);
                auditService.logAudit(taskCode, tradeDate, "THS",
                        chTable, "SUCCESS", clickhouseCount, clickhouseCount, 0, null);
                log.info("  ↳ {} → SUCCESS (CH验证: {}行)", taskCode, clickhouseCount);
            } else {
                throw new RuntimeException("ClickHouse 无数据: table=" + chTable + ", tradeDate=" + tradeDate);
            }
        } catch (Exception e) {
            auditService.markFailed(taskCode, tradeDate, "THS", e.getMessage());
            auditService.logAudit(taskCode, tradeDate, "THS",
                    resolveThsTargetTable(taskCode), "FAILED", 0, 0, 0, e.getMessage());
            log.warn("  ↳ {} → FAILED: {}", taskCode, e.getMessage());
        }
    }

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

    private String resolveThsTargetTable(String taskCode) {
        return switch (taskCode) {
            case "ths_plate_dimension", "ths_plates" -> "stock_plate_dimension";
            case "ths_plate_relation", "ths_relations" -> "stock_plate_relation_snapshot";
            default -> taskCode;
        };
    }

    private void sleep(long seconds) {
        try { Thread.sleep(seconds * 1000); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}