package com.wushi.module.spider.job;

import com.wushi.module.market.domain.row.*;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.core.SpiderWriteResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneySpiderClient;
import com.wushi.module.spider.enums.PoolType;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.kline.StockKlineProvider;
import com.wushi.module.spider.provider.limit.LimitStatusProvider;
import com.wushi.module.spider.provider.plate.PlateDimensionProvider;
import com.wushi.module.spider.provider.plate.StockPlateRelationProvider;
import com.wushi.module.spider.service.SpiderIngestionService;
import com.wushi.module.spider.ths.ThsSpiderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * 爬虫跑批任务编排器
 * 协调东财API + 同花顺Selenium 双数据源抓取
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpiderJob {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;
    private final SpiderIngestionService ingestionService;
    private final ThsSpiderServiceImpl thsSpiderService;

    // 东财 Provider (通过 Spring DI 注入)
    private final List<StockKlineProvider> stockKlineProviders;
    private final List<PlateDimensionProvider> plateDimensionProviders;
    private final List<StockPlateRelationProvider> stockPlateRelationProviders;
    private final List<LimitStatusProvider> limitStatusProviders;

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

        // 2. 板块维度 + 板块日K
        int plateDimInserted = runEastMoneyPlateDimensions(tradeDate);
        result.put("plateDimInserted", plateDimInserted);

        // 3. 板块个股关系
        int relationInserted = runEastMoneyPlateRelations(tradeDate);
        result.put("relationInserted", relationInserted);

        // 4. 涨跌停状态
        int limitStatusInserted = runEastMoneyLimitStatus(tradeDate);
        result.put("limitStatusInserted", limitStatusInserted);

        // 5. 各股票池快照
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
            try {
                EastMoneySpiderClient.EastMoneyPoolResult poolResult =
                        eastMoneySpiderClient.fetchPool(entry.getKey(), tradeDate);
                List<StockPoolDailySnapshotRow> rows = poolResult.rows().stream()
                        .map(node -> fieldMapper.toPoolSnapshot(tradeDate, entry.getValue().getCode(), node))
                        .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                        .toList();

                SpiderResult<StockPoolDailySnapshotRow> spiderResult = SpiderResult.<StockPoolDailySnapshotRow>builder()
                        .taskCode("pool_" + entry.getValue().getCode())
                        .provider(SpiderProviderType.EAST_MONEY.name())
                        .status(SpiderTaskStatus.SUCCESS)
                        .rows(rows)
                        .fetchedCount(poolResult.totalCount())
                        .successCount(rows.size())
                        .build();

                SpiderWriteResult writeResult = ingestionService.ingest(spiderResult);
                results.put(entry.getValue().getCode(), writeResult.getInsertedCount());
                log.info("东财股票池抓取完成: type={}, count={}", entry.getValue().getCode(), writeResult.getInsertedCount());

                sleep(300); // 接口礼貌延迟
            } catch (Exception e) {
                log.error("东财股票池抓取失败: type={}, error={}", entry.getValue().getCode(), e.getMessage());
                results.put(entry.getValue().getCode(), 0);
            }
        }
        return results;
    }

    /**
     * 同花顺日跑批
     */
    public Map<String, Object> runThsDaily(LocalDate tradeDate) {
        log.info("===== 同花顺日跑批开始: tradeDate={} =====", tradeDate);
        try {
            Map<String, Object> result = thsSpiderService.syncDaily(tradeDate);
            log.info("===== 同花顺日跑批完成: {} =====", result);
            return result;
        } catch (Exception e) {
            log.error("同花顺日跑批失败: {}", e.getMessage(), e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 同花顺仅抓取板块维度
     */
    public Map<String, Object> runThsPlates(LocalDate tradeDate) {
        try {
            List<StockPlateDimensionRow> plates = thsSpiderService.fetchAllPlates(tradeDate);
            SpiderResult<StockPlateDimensionRow> result = SpiderResult.<StockPlateDimensionRow>builder()
                    .taskCode("ths_plate_dimension")
                    .provider(SpiderProviderType.TONG_HUA_SHUN.name())
                    .status(SpiderTaskStatus.SUCCESS)
                    .rows(plates)
                    .fetchedCount(plates.size())
                    .successCount(plates.size())
                    .build();
            SpiderWriteResult writeResult = ingestionService.ingest(result);
            return Map.of("plateCount", plates.size(), "inserted", writeResult.getInsertedCount());
        } catch (Exception e) {
            log.error("同花顺板块抓取失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 同花顺仅抓取板块个股关系
     */
    public Map<String, Object> runThsRelations(LocalDate tradeDate) {
        try {
            List<StockPlateRelationSnapshotRow> relations = thsSpiderService.fetchAllPlateRelations(tradeDate);
            SpiderResult<StockPlateRelationSnapshotRow> result = SpiderResult.<StockPlateRelationSnapshotRow>builder()
                    .taskCode("ths_plate_relation")
                    .provider(SpiderProviderType.TONG_HUA_SHUN.name())
                    .status(SpiderTaskStatus.SUCCESS)
                    .rows(relations)
                    .fetchedCount(relations.size())
                    .successCount(relations.size())
                    .build();
            SpiderWriteResult writeResult = ingestionService.ingest(result);
            return Map.of("relationCount", relations.size(), "inserted", writeResult.getInsertedCount());
        } catch (Exception e) {
            log.error("同花顺板块个股关系抓取失败: {}", e.getMessage());
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private int runEastMoneyStockDailyKline(LocalDate tradeDate) {
        try {
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (StockKlineProvider provider : stockKlineProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockDailyKlineRow> result = provider.fetchStockDailyKline(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财股票日K抓取失败: {}", e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyPlateDimensions(LocalDate tradeDate) {
        try {
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (PlateDimensionProvider provider : plateDimensionProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockPlateDimensionRow> result = provider.fetchPlateDimension(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财板块维度抓取失败: {}", e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyPlateRelations(LocalDate tradeDate) {
        try {
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (StockPlateRelationProvider provider : stockPlateRelationProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockPlateRelationSnapshotRow> result = provider.fetchStockPlateRelations(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财板块个股关系抓取失败: {}", e.getMessage());
            return 0;
        }
    }

    private int runEastMoneyLimitStatus(LocalDate tradeDate) {
        try {
            SpiderFetchRequest request = SpiderFetchRequest.builder()
                    .tradeDate(tradeDate)
                    .providerType(SpiderProviderType.EAST_MONEY)
                    .build();
            for (LimitStatusProvider provider : limitStatusProviders) {
                if (provider.providerType() == SpiderProviderType.EAST_MONEY) {
                    SpiderResult<StockLimitStatusDailyRow> result = provider.fetchLimitStatus(request);
                    SpiderWriteResult writeResult = ingestionService.ingest(result);
                    return writeResult.getInsertedCount();
                }
            }
            return 0;
        } catch (Exception e) {
            log.error("东财涨跌停状态抓取失败: {}", e.getMessage());
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
