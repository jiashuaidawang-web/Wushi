package com.wushi.module.spider.provider.plate;

import com.wushi.module.market.domain.row.StockPlateRelationSnapshotRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface StockPlateRelationProvider extends SpiderProvider {

    SpiderResult<StockPlateRelationSnapshotRow> fetchStockPlateRelations(SpiderFetchRequest request);
}
