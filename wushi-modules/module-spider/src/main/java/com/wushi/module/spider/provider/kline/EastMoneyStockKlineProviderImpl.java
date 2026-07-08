package com.wushi.module.spider.provider.kline;

import com.wushi.module.market.domain.row.StockDailyKlineRow;
import com.wushi.module.market.domain.row.StockMinuteKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.eastmoney.EastMoneyEndpoint;
import com.wushi.module.spider.eastmoney.EastMoneyFieldMapper;
import com.wushi.module.spider.eastmoney.EastMoneySpiderClient;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.kline.StockKlineProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EastMoneyStockKlineProviderImpl implements StockKlineProvider {

    private final EastMoneySpiderClient eastMoneySpiderClient;
    private final EastMoneyFieldMapper fieldMapper;

    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<StockDailyKlineRow> fetchStockDailyKline(SpiderFetchRequest request) {
        LocalDate tradeDate = request.getTradeDate();
        log.info("开始抓取东财股票日K: tradeDate={}", tradeDate);
        try {
            var result = eastMoneySpiderClient.fetchPaged(EastMoneyEndpoint.ALL_STOCK);
            List<StockDailyKlineRow> rows = result.rows().stream()
                    .map(node -> fieldMapper.toStockDailyKline(tradeDate, node))
                    .filter(row -> row.stockCode() != null && !row.stockCode().isBlank())
                    .toList();
            log.info("东财股票日K抓取完成: total={}, mapped={}", result.totalCount(), rows.size());
            return SpiderResult.<StockDailyKlineRow>builder()
                    .taskCode("stock_daily_kline").provider(SpiderProviderType.EAST_MONEY.name())
                    .status(SpiderTaskStatus.SUCCESS).rows(rows)
                    .fetchedCount(result.totalCount()).successCount(rows.size()).build();
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
