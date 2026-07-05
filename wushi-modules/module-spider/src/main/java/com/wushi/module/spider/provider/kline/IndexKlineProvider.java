package com.wushi.module.spider.provider.kline;

import com.wushi.module.market.domain.row.IndexDailyKlineRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface IndexKlineProvider extends SpiderProvider {

    SpiderResult<IndexDailyKlineRow> fetchIndexDailyKline(SpiderFetchRequest request);
}
