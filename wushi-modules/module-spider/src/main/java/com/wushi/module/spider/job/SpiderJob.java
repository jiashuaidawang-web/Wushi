package com.wushi.module.spider.job;

import com.wushi.module.market.domain.row.*;
import com.wushi.module.spider.checkpoint.SpiderCheckpointService;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.core.SpiderWriteResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneySpiderClient;
import com.wushi.module.spider.enums.PoolType;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.kline.IndexKlineProvider;
import com.wushi.module.spider.provider.kline.PlateKlineProvider;
import com.wushi.module.spider.provider.kline.StockKlineProvider;
import com.wushi.module.spider.provider.limit.LimitIntradayEventProvider;
import com.wushi.module.spider.provider.limit.LimitStatusProvider;
import com.wushi.module.spider.provider.market.CapitalFlowProvider;
import com.wushi.module.spider.provider.plate.PlateDimensionProvider;
import com.wushi.module.spider.provider.plate.StockPlateRelationProvider;
import com.wushi.module.spider.service.SpiderIngestionService;
import com.wushi.module.spider.ths.ThsPlaywrightSpiderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 爬虫跑批任务编排器
 * 协调东财API + 同花顺Playwright 双数据源抓取
 * 支持断点续传: 同一天同一 provider 已成功则跳过
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpiderJob {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;
    private final SpiderIngestionService ingestionService;
    private final ThsPlaywrightSpiderServiceImpl thsSpiderService;
    private final SpiderCheckpointService checkpointService;

    // 东财 Provider (通过 Spring DI 注入)
    private final List<StockKlineProvider> stockKlineProviders;
    private final List<IndexKlineProvider> indexKlineProviders;
    private final List<PlateKlineProvider> plateKlineProviders;
    private final List<PlateDimensionProvider> plateDimensionProviders;
    private final List<StockPlateRelationProvider> stockPlateRelationProviders;
    private final List<LimitStatusProvider> limitStatusProviders;
    private final List<LimitIntradayEventProvider> limitIntradayEventProviders;
    private final List<CapitalFlowProvider> capitalFlowProviders;

    /**
     * 东财全量日跑批
     */
    public Map<String, Object> runEastMoneyDaily(LocalDate tradeDate) {
        log.info("===== 东财日跑批开始: tradeDate={} =====", tradeDate);
        Map<String, Object> result = new LinkedHashMap<>();
        long startMs = System.currentTimeMillis();

        // 1. 股票日K
        int stockDailyInserted = runEastMoneyStockDailyKline(tradeDate);
        result.put("stockDailyInserted", stockDailyInserted);

        // 2. 指数日K
        int indexDailyInserted = runEastMoneyIndexDailyKline(tradeDate);
        result.put("indexDailyInserted", indexDailyInserted);

        // 3. 板块维度
        int plateDimInserted = runEastMoneyPlateDimensions(tradeDate);
        result.put("plateDimInserted", plateDimInserted);

        // 4. 板块日K
        int plateKlineInserted = runEastMoneyPlateDailyKline(tradeDate);
        result.put("plateKlineInserted", plateKlineInserted);

        // 5. 板块个股关系
        int relationInserted = runEastMoneyPlateRelations(tradeDate);
        result.put("relationInserted", relationInserted);

        // 6. 涨跌停状态
        int limitStatusInserted = runEastMoneyLimitStatus(tradeDate);
        result.put("limitStatusInserted", limitStatusInserted);

        // 7. 涨停盘中事件
        int limitEventInserted = runEastMoneyLimitIntradayEvents(tradeDate);
        result.put("limitEventInserted", limitEventInserted);

        // 8. 资金流向
        int capitalFlowInserted = runEastMoneyCapitalFlow(tradeDate);
        result.put("capitalFlowInserted", capitalFlowInserted);

        // 9. 各股票池快照
        Map<String, Integer> poolResults = runEastMoneyPools(tradeDate);
        result.put("pools", poolResults);

        long elapsed = System.currentTimeMillis() - startMs;
        result.put("elapsedMs", elapsed);
        log.info("===== 东财日跑批完成: {} =====", result);
        return result;
    }

    /**
     * 东财股票池跑批(涨停/跌停/强势/连板/炸板)
     */
    public Map<String, Integer> runEastMoneyPools(LocalDate tradeDate) {
        Map<String, Integer> results = new LinkedHashMap<>();
        List<Map.Entry<EastMoneyEndpoint, PoolType>> poolEndpoints = List.of(
                Map.entry(EastMoneyEndpoint.LIMIT_UP_POOL, PoolType.LIMIT_UP),
                Map.entry(EastMoneyEndpoint.YEST_LIMIT_UP_POOL, PoolType.YEST_LIMIT_UP),
                Map.entry(EastMoneyEndpoint.STRONG_POOL, PoolType.STRONG),
                Map.entry(EastMoneyEndpoint.SUB_NEW_POOL, PoolType.SUB_NEW),
                Map.entry(EastMoneyEndpoint.BROKEN_POOL, PoolType.BROKEN),
                Map.entry(EastMoneyEndpoint.LIMIT_DOWN_POOL, PoolType.LIMIT_DOWN)
        );

        for (var entry : poolEndpoints) {
            String taskCode = "pool_" + entry.getValue().getCode();
            try {
                if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                    log.info("股票池任务已跳过(今日已成功): {}, tradeDate={}", taskCode, tradeDate);
                    results.put(entry.getValue().getCode(), 0);
                    continue;
                }
                checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());

                EastMoneySpiderClient.EastMoneyPoolResult poolResult =
                        eastMoneySpiderClient.fetchPool(entry.getKey(), tradeDate);
                List<StockPoolDailySnapshotRow> rows = poolResult.rows().stream()
                        .map(node -> fieldMapper.toPoolSnapshot(tradeDate, entry.getValue().getCode(), node))
                        .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                        .toList();

                SpiderResult<StockPoolDailySnapshotRow> spiderResult = SpiderResult.<StockPoolDailySnapshotRow>builder()
                        .taskCode(taskCode)
                        .provider(SpiderProviderType.EAST_MONEY.name())
                        .status(SpiderTaskStatus.SUCCESS)
                        .rows(rows)
                        .fetchedCount(poolResult.totalCount())
                        .successCount(rows.size())
                        .build();
                SpiderWriteResult writeResult = ingestionService.ingest(spiderResult);
                checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                        rows.size(), 0, null);
                results.put(entry.getValue().getCode(), writeResult.getInsertedCount());
            } catch (Exception e) {
                log.error("东财股票池抓取失败: {}, error={}", entry.getValue().getCode(), e.getMessage());
                checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
                results.put(entry.getValue().getCode(), 0);
            }
        }
        return results;
    }

    /**
     * 同时跑东财 + 同花顺的全量任务
     */
    public Map<String, Object> runFullDaily(LocalDate tradeDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(runEastMoneyDaily(tradeDate));
        result.putAll(runThsRelations(tradeDate));
        return result;
    }

    /**
     * 同花顺仅抓取板块个股关系
     */
    public Map<String, Object> runThsRelations(LocalDate tradeDate) {
        String taskCode = "ths_plate_relation";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name())) {
                log.info("同花顺任务已跳过(今日已成功): tradeDate={}", tradeDate);
                return Map.of("relationCount", 0, "inserted", 0);
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name());

            List<StockPlateRelationSnapshotRow> relations = thsSpiderService.fetchAllPlateRelations(tradeDate);
            SpiderResult<StockPlateRelationSnapshotRow> result = SpiderResult.<StockPlateRelationSnapshotRow>builder()
                    .taskCode(taskCode)
                    .provider(SpiderProviderType.TONG_HUA_SHUN.name())
                    .status(SpiderTaskStatus.SUCCESS)
                    .rows(relations)
                    .fetchedCount(relations.size())
                    .successCount(relations.size())
                    .build();
            SpiderWriteResult writeResult = ingestionService.ingest(result);
            checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name(),
                    relations.size(), 0, null);
            return Map.of("relationCount", relations.size(), "inserted", writeResult.getInsertedCount());
        } catch (Exception e) {
            log.error("同花顺板块个股关系抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name(), e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 同花顺全量日跑批 (板块维度 + 板块个股关系)
     */
    public Map<String, Object> runThsDaily(LocalDate tradeDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plates", runThsPlates(tradeDate));
        result.put("relations", runThsRelations(tradeDate));
        return result;
    }

    /**
     * 同花顺仅抓取板块维度
     */
    public Map<String, Object> runThsPlates(LocalDate tradeDate) {
        String taskCode = "ths_plate_dimension";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name())) {
                log.info("同花顺板块维度任务已跳过(今日已成功): tradeDate={}", tradeDate);
                return Map.of("plateCount", 0, "inserted", 0);
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name());

            List<StockPlateDimensionRow> plates = thsSpiderService.fetchAllPlates(tradeDate);
            SpiderResult<StockPlateDimensionRow> spiderResult = SpiderResult.<StockPlateDimensionRow>builder()
                    .taskCode(taskCode)
                    .provider(SpiderProviderType.TONG_HUA_SHUN.name())
                    .status(SpiderTaskStatus.SUCCESS)
                    .rows(plates)
                    .fetchedCount(plates.size())
                    .successCount(plates.size())
                    .build();
            SpiderWriteResult writeResult = ingestionService.ingest(spiderResult);
            checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name(),
                    plates.size(), 0, null);
            return Map.of("plateCount", plates.size(), "inserted", writeResult.getInsertedCount());
        } catch (Exception e) {
            log.error("同花顺板块维度抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.TONG_HUA_SHUN.name(), e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private int runEastMoneyStockDailyKline(LocalDate tradeDate) {
        String taskCode = "stock_daily_kline";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("股票日K已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (StockKlineProvider provider : stockKlineProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockDailyKlineRow> result = provider.fetchStockDailyKline(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财股票日K抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyIndexDailyKline(LocalDate tradeDate) {
        String taskCode = "index_daily_kline";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("指数日K已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (IndexKlineProvider provider : indexKlineProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<IndexDailyKlineRow> result = provider.fetchIndexDailyKline(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财指数日K抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyPlateDailyKline(LocalDate tradeDate) {
        String taskCode = "plate_daily_kline";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("板块日K已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (PlateKlineProvider provider : plateKlineProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockPlateDailyKlineRow> result = provider.fetchPlateDailyKline(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财板块日K抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyPlateDimensions(LocalDate tradeDate) {
        String taskCode = "plate_dimension";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("板块维度已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (PlateDimensionProvider provider : plateDimensionProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockPlateDimensionRow> result = provider.fetchPlateDimension(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财板块维度抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyPlateRelations(LocalDate tradeDate) {
        String taskCode = "plate_relation";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("板块个股关系已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (StockPlateRelationProvider provider : stockPlateRelationProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockPlateRelationSnapshotRow> result = provider.fetchStockPlateRelations(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财板块个股关系抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyLimitStatus(LocalDate tradeDate) {
        String taskCode = "stock_limit_status";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("涨跌停状态已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (LimitStatusProvider provider : limitStatusProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockLimitStatusDailyRow> result = provider.fetchLimitStatus(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财涨跌停状态抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyLimitIntradayEvents(LocalDate tradeDate) {
        String taskCode = "limit_intraday_event";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("涨停盘中事件已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (LimitIntradayEventProvider provider : limitIntradayEventProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockLimitIntradayEventRow> result = provider.fetchLimitIntradayEvents(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财涨停盘中事件抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyCapitalFlow(LocalDate tradeDate) {
        String taskCode = "capital_flow";
        try {
            if (checkpointService.isAlreadySucceeded(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name())) {
                log.info("资金流向已跳过(今日已成功): tradeDate={}", tradeDate);
                return 0;
            }
            checkpointService.markRunning(taskCode, taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name());
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (CapitalFlowProvider provider : capitalFlowProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<CapitalFlowDailySnapshotRow> result = provider.fetchCapitalFlow(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    checkpointService.markSuccess(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(),
                            result.getSuccessCount(), result.getFailedCount(), null);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财资金流向抓取失败: {}", e.getMessage());
            checkpointService.markFailed(taskCode, tradeDate, SpiderProviderType.EAST_MONEY.name(), e.getMessage());
            return 0;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
