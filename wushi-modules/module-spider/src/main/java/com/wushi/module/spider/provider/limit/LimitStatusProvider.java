package com.wushi.module.spider.provider.limit;

import com.wushi.module.market.domain.row.StockLimitStatusDailyRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface LimitStatusProvider extends SpiderProvider {

    SpiderResult<StockLimitStatusDailyRow> fetchLimitStatus(SpiderFetchRequest request);
}
