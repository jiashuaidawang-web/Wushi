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
import com.wushi.module.spider.provider.kline.StockKlineProvider;
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
            int total = playwrightClient.fetchAllStocks(rawRows);
            List<StockDailyKlineRow> rows = rawRows.stream()
                    .map(node -> fieldMapper.toStockDailyKline(tradeDate, node))
                    .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                    .toList();
            log.info("东财股票日K抓取完成: total={}, mapped={}", total, rows.size());
            if (total == 0) {
                log.error("东财股票日K抓取结果为空，标记为失败");
                return SpiderResult.<StockDailyKlineRow>builder()
                        .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                        .status(SpiderTaskStatus.FAILED).errorMessage("Playwright 抓取返回 0 条数据").build();
            }
            return SpiderResult.<StockDailyKlineRow>builder()
                    .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(total).successCount(rows.size()).build();
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
