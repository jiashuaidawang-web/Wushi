package com.wushi.module.spider.controller;

import com.wushi.module.spider.job.SpiderJob;
import com.wushi.module.market.service.MarketSnapshotAggregationService;
import com.wushi.module.spider.ths.ThsProxyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/spider")
@RequiredArgsConstructor
public class SpiderController {

    private final SpiderJob spiderJob;
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
