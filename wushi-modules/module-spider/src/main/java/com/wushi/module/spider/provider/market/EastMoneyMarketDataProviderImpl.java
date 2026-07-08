package com.wushi.module.spider.provider.market;

import com.wushi.module.market.domain.row.MarketBreadthDailySnapshotRow;
import com.wushi.module.market.domain.row.StockPoolDailySnapshotRow;
import com.wushi.module.market.domain.row.TradingCalendarRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.enums.SpiderProviderType;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.provider.market.MarketDataProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EastMoneyMarketDataProviderImpl implements MarketDataProvider {
    @Override
    public SpiderProviderType providerType() { return SpiderProviderType.EAST_MONEY; }

    @Override
    public SpiderResult<TradingCalendarRow> fetchTradingCalendar(SpiderFetchRequest request) {
        return SpiderResult.<TradingCalendarRow>builder()
                .taskCode("trading_calendar").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }

    @Override
    public SpiderResult<StockPoolDailySnapshotRow> fetchStockPool(SpiderFetchRequest request) {
        return SpiderResult.<StockPoolDailySnapshotRow>builder()
                .taskCode("stock_pool").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }

    @Override
    public SpiderResult<MarketBreadthDailySnapshotRow> fetchMarketBreadth(SpiderFetchRequest request) {
        return SpiderResult.<MarketBreadthDailySnapshotRow>builder()
                .taskCode("market_breadth").provider(SpiderProviderType.EAST_MONEY.name())
                .status(SpiderTaskStatus.SKIPPED).rows(List.of()).fetchedCount(0).build();
    }
}
