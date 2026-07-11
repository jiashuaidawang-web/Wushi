package com.wushi.module.spider.provider.kline;

import com.fasterxml.jackson.databind.JsonNode;
import com.wushi.module.market.domain.row.StockDailyKlineRow;
import com.wushi.module.market.domain.row.StockMinuteKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneyPlaywrightClient;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyStockKlineProviderImpl implements StockKlineProvider {

    /** 如果实际抓取行数 < API total 的此比例，视为抓取不完整，标记失败 */
    private static final double MIN_FETCH_RATIO = 0.95;

    private final EastMoneyPlaywrightClient playwrightClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockDailyKlineRow> fetchStockDailyKline(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财股票日K: tradeDate={}", tradeDate);
        try {
            List<JsonNode> rawRows = new ArrayList<>();
            int fetchedCount = playwrightClient.fetchAllStocks(rawRows);
            List<StockDailyKlineRow> rows = rawRows.stream()
                    .map(node -> fieldMapper.toStockDailyKline(tradeDate, node))
                    .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                    .toList();
            log.info("东财股票日K抓取完成: fetched={}, mapped={}", fetchedCount, rows.size());

            if (fetchedCount == 0) {
                return SpiderResult.<StockDailyKlineRow>builder()
                        .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                        .status(SpiderTaskStatus.FAILED).errorMessage("Playwright 抓取返回 0 条").build();
            }

            // 完整性校验：抓取数量不能明显少于预期
            if (fetchedCount < 1000 || rows.size() < fetchedCount * MIN_FETCH_RATIO) {
                log.warn("东财股票日K抓取不完整: fetched={}, mapped={}, 标记FAILED以便重试", fetchedCount, rows.size());
                return SpiderResult.<StockDailyKlineRow>builder()
                        .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                        .status(SpiderTaskStatus.FAILED)
                        .errorMessage("抓取不完整: fetched=" + fetchedCount + ", mapped=" + rows.size())
                        .rows(rows).fetchedCount(fetchedCount).successCount(rows.size()).build();
            }

            return SpiderResult.<StockDailyKlineRow>builder()
                    .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(fetchedCount).successCount(rows.size()).build();
        } catch (Exception e) {
            log.error("东财股票日K抓取失败: {}", e.getMessage(), e);
            return SpiderResult.<StockDailyKlineRow>builder()
                    .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.FAILED).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public SpiderResult<StockMinuteKlineRow> fetchStockMinuteKline(SpiderFetchRequest request) {
        return SpiderResult.<StockMinuteKlineRow>builder()
                .taskCode("stock_minute_kline").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }
}
