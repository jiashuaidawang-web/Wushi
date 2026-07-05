package com.wushi.module.spider.provider.plate;

import com.wushi.module.market.domain.row.StockPlateDimensionRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface PlateDimensionProvider extends SpiderProvider {

    SpiderResult<StockPlateDimensionRow> fetchPlateDimension(SpiderFetchRequest request);
}
