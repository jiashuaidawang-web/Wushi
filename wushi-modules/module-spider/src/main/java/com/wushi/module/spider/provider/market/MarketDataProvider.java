package com.wushi.module.spider.provider.market;

import com.wushi.module.market.domain.row.MarketBreadthDailySnapshotRow;
import com.wushi.module.market.domain.row.StockPoolDailySnapshotRow;
import com.wushi.module.market.domain.row.TradingCalendarRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface MarketDataProvider extends SpiderProvider {

    SpiderResult<TradingCalendarRow> fetchTradingCalendar(SpiderFetchRequest request);

    SpiderResult<StockPoolDailySnapshotRow> fetchStockPool(SpiderFetchRequest request);

    SpiderResult<MarketBreadthDailySnapshotRow> fetchMarketBreadth(SpiderFetchRequest request);
}
