package com.wushi.module.spider.provider.limit;

import com.wushi.module.market.domain.row.StockLimitIntradayEventRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface LimitIntradayEventProvider extends SpiderProvider {

    SpiderResult<StockLimitIntradayEventRow> fetchLimitIntradayEvents(SpiderFetchRequest request);
}
