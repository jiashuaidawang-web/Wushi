package com.wushi.module.spider.controller;

import com.wushi.module.spider.job.SpiderJob;
import com.wushi.module.market.service.MarketSnapshotAggregationService;
import com.wushi.module.spider.audit.service.SpiderAuditService;
import com.wushi.module.spider.audit.service.SpiderBatchOrchestratorService;
import com.wushi.module.spider.ths.ThsProxyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/spider")
@RequiredArgsConstructor
public class SpiderController {

    private final SpiderJob spiderJob;
    private final SpiderBatchOrchestratorService batchOrchestratorService;
    private final SpiderAuditService auditService;
    private final ThsProxyProvider thsProxyProvider;
    private final MarketSnapshotAggregationService aggregationService;

    /**
     * 东财全量日跑批
     * GET /api/spider/dc/daily?tradeDate=2026-07-08
     */
    @GetMapping("/dc/daily")
    public ApiResponse<Map<String, Object>> runEastMoneyDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("东财日跑批触发: tradeDate={}", realTradeDate);
        try {
            return ApiResponse.success(spiderJob.runEastMoneyDaily(realTradeDate));
        } catch (Exception e) {
            log.error("东财日跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("东财跑批失败: " + e.getMessage());
        }
    }

    /**
     * 东财仅跑股票池
     * GET /api/spider/dc/pools?tradeDate=2026-07-08
     */
    @GetMapping("/dc/pools")
    public ApiResponse<Map<String, Integer>> runEastMoneyPools(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("东财股票池跑批触发: tradeDate={}", realTradeDate);
        try {
            return ApiResponse.success(spiderJob.runEastMoneyPools(realTradeDate));
        } catch (Exception e) {
            log.error("东财股票池跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("东财股票池跑批失败: " + e.getMessage());
        }
    }

    /**
     * 同花顺全量日跑批
     * GET /api/spider/ths/daily?tradeDate=2026-07-08
     */
    @GetMapping("/ths/daily")
    public ApiResponse<Map<String, Object>> runThsDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("同花顺日跑批触发: tradeDate={}", realTradeDate);
        try {
            return ApiResponse.success(spiderJob.runThsDaily(realTradeDate));
        } catch (Exception e) {
            log.error("同花顺日跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("同花顺跑批失败: " + e.getMessage());
        }
    }

    /**
     * 同花顺仅跑板块
     * GET /api/spider/ths/plates?tradeDate=2026-07-08
     */
    @GetMapping("/ths/plates")
    public ApiResponse<Map<String, Object>> runThsPlates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        try {
            return ApiResponse.success(spiderJob.runThsPlates(realTradeDate));
        } catch (Exception e) {
            log.error("同花顺板块跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("同花顺板块跑批失败: " + e.getMessage());
        }
    }

    /**
     * 同花顺仅跑板块个股关系
     * GET /api/spider/ths/relations?tradeDate=2026-07-08
     */
    @GetMapping("/ths/relations")
    public ApiResponse<Map<String, Object>> runThsRelations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        try {
            return ApiResponse.success(spiderJob.runThsRelations(realTradeDate));
        } catch (Exception e) {
            log.error("同花顺关系跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("同花顺关系跑批失败: " + e.getMessage());
        }
    }

    /**
     * 代理池预热/状态
     * GET /api/spider/ths/proxy/warmup
     */
    @GetMapping("/ths/proxy/warmup")
    public ApiResponse<Map<String, Object>> warmupThsProxyPool() {
        return ApiResponse.success(thsProxyProvider.warmup());
    }

    /**
     * 聚合市场快照（plate_daily + market_breadth）
     * GET /api/spider/aggregate?tradeDate=2026-07-08
     */
    @GetMapping("/aggregate")
    public ApiResponse<Map<String, Object>> aggregateSnapshots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("市场快照聚合触发: tradeDate={}", realTradeDate);
        try {
            return ApiResponse.success(aggregationService.aggregateBoth(realTradeDate));
        } catch (Exception e) {
            log.error("市场快照聚合失败: {}", e.getMessage(), e);
            return ApiResponse.error("聚合失败: " + e.getMessage());
        }
    }

    /**
     * 【长期方案】带完整对账 + 指数退避重试的跑批
     * GET /api/spider/dc/daily/complete?tradeDate=2026-07-08
     *
     * 遇到 502 等瞬态错误会自动重试(最多 5 轮),每轮退避 10s → 20s → 40s → 80s
     * 返回: {tradeDate, complete=true/false, elapsedMs}
     */
    @GetMapping("/dc/daily/complete")
    public ApiResponse<Map<String, Object>> runDailyComplete(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("触发[完整版]跑批: tradeDate={}", realTradeDate);
        long startMs = System.currentTimeMillis();
        try {
            boolean complete = batchOrchestratorService.runUntilComplete(realTradeDate);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tradeDate", realTradeDate.toString());
            data.put("complete", complete);
            data.put("elapsedMs", System.currentTimeMillis() - startMs);
            if (!complete) {
                data.put("detail", "达到最大重试次数后仍有失败,请查看审计日志");
                data.put("failedTasks", auditService.findFailedTasks(realTradeDate).stream()
                        .map(com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity::getTaskCode)
                        .distinct().toList());
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("完整版跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("跑批异常: " + e.getMessage());
        }
    }

    /**
     * 对账查询 API — 查看某交易日所有任务状态
     * GET /api/spider/dc/daily/status?tradeDate=2026-07-08
     *
     * 返回每个 task 的 status(SUCCESS/FAILED/PARTIAL)、successCount、failCount
     */
    @GetMapping("/dc/daily/status")
    public ApiResponse<Map<String, Object>> checkStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("触发对账查询: tradeDate={}", realTradeDate);
        try {
            List<com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity> all =
                    auditService.findAllTasks(realTradeDate);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tradeDate", realTradeDate.toString());
            data.put("totalTasks", all.size());
            data.put("allTasks", all.stream().map(t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskCode", t.getTaskCode());
                row.put("status", t.getStatus());
                row.put("successCount", t.getSuccessCount());
                row.put("failCount", t.getFailCount());
                row.put("errorMessage", t.getErrorMessage());
                row.put("startedAt", t.getStartedAt() != null ? t.getStartedAt().toString() : null);
                row.put("finishedAt", t.getFinishedAt() != null ? t.getFinishedAt().toString() : null);
                return row;
            }).toList());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("对账查询失败: {}", e.getMessage(), e);
            return ApiResponse.error("对账异常: " + e.getMessage());
        }
    }

    /**
     * 同花顺完整跑批(幂等 + 失败重试)
     * GET /api/spider/ths/daily/complete?tradeDate=2026-07-10
     *
     * 遇到 502 等瞬态错误会自动重试,每轮退避 2min → 5min → 10min → 30min
     * 返回: {tradeDate, complete=true/false, elapsedMs}
     */
    @GetMapping("/ths/daily/complete")
    public ApiResponse<Map<String, Object>> runThsDailyComplete(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("触发[THS完整版]跑批: tradeDate={}", realTradeDate);
        long startMs = System.currentTimeMillis();
        try {
            boolean complete = batchOrchestratorService.runThsUntilComplete(realTradeDate);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tradeDate", realTradeDate.toString());
            data.put("complete", complete);
            data.put("elapsedMs", System.currentTimeMillis() - startMs);
            if (!complete) {
                data.put("detail", "达到最大重试次数后仍有失败,请查看审计日志");
                data.put("failedTasks", auditService.findFailedTasks(realTradeDate).stream()
                        .map(com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity::getTaskCode)
                        .distinct().toList());
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("THS完整版跑批失败: {}", e.getMessage(), e);
            return ApiResponse.error("跑批异常: " + e.getMessage());
        }
    }

    /**
     * 同花顺对账查询 API
     * GET /api/spider/ths/daily/status?tradeDate=2026-07-10
     */
    @GetMapping("/ths/daily/status")
    public ApiResponse<Map<String, Object>> checkThsStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate) {
        LocalDate realTradeDate = tradeDate == null ? LocalDate.now() : tradeDate;
        log.info("触发THS对账查询: tradeDate={}", realTradeDate);
        try {
            List<com.wushi.module.spider.audit.entity.SpiderTaskCheckpointEntity> all =
                    auditService.findAllTasks(realTradeDate);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tradeDate", realTradeDate.toString());
            data.put("totalTasks", all.size());
            data.put("allTasks", all.stream().map(t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("taskCode", t.getTaskCode());
                row.put("status", t.getStatus());
                row.put("successCount", t.getSuccessCount());
                row.put("failCount", t.getFailCount());
                row.put("errorMessage", t.getErrorMessage());
                return row;
            }).toList());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("THS对账查询失败: {}", e.getMessage(), e);
            return ApiResponse.error("对账异常: " + e.getMessage());
        }
    }

    /**
     * 通用响应封装
     */
    public record ApiResponse<T>(boolean success, String code, String message, T data) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, "OK", "success", data);
        }
        public static <T> ApiResponse<T> error(String message) {
            return new ApiResponse<>(false, "ERROR", message, null);
        }
    }
}
