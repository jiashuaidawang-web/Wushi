package com.wushi.module.spider.job;

import com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity;
import com.wushi.module.spider.audit.service.SpiderAuditService;
import com.wushi.module.spider.audit.service.SpiderBatchOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日收盘后定时跑批
 * 
 * 触发时间:每天 15:05 (收盘后 5 分钟,确保数据已落定)
 * 跑取数据:当天
 * 失败策略:指数退避重试到当天 23:59
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderDailyScheduler {

    private final SpiderBatchOrchestratorService orchestratorService;
    private final SpiderAuditService auditService;

    /**
     * 每天 15:05:00 自动执行
     */
    @Scheduled(cron = "0 5 15 * * *")
    public void runOnMarketClose() {
        // 收盘后跑当天数据
        LocalDate tradeDate = LocalDate.now();
        log.info("📈 收盘定时任务触发: tradeDate={}, 当前时间={}", tradeDate, java.time.LocalDateTime.now());

        boolean complete = orchestratorService.runUntilComplete(tradeDate);

        // 对账:还有失败?
        List<SpiderTaskCheckpointEntity> failed = auditService.findFailedTasks(tradeDate);
        if (!failed.isEmpty()) {
            log.error("🚨 定时任务完成但仍有失败! tradeDate={}, failed={}, 请人工介入!",
                    tradeDate,
                    failed.stream().map(SpiderTaskCheckpointEntity::getTaskCode).toList());
        } else {
            log.info("✅ 定时任务完成: tradeDate={}, 全部成功", tradeDate);
        }
    }
}
