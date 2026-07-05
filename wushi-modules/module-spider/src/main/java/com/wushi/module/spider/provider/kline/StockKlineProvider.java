package com.wushi.module.spider.provider.kline;

import com.wushi.module.market.domain.row.StockDailyKlineRow;
import com.wushi.module.market.domain.row.StockMinuteKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface StockKlineProvider extends SpiderProvider {

    SpiderResult<StockDailyKlineRow> fetchStockDailyKline(SpiderFetchRequest request);

    SpiderResult<StockMinuteKlineRow> fetchStockMinuteKline(SpiderFetchRequest request);
}
