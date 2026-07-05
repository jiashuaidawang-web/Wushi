package com.wushi.module.spider.provider.market;

import com.wushi.module.market.domain.row.CapitalFlowDailySnapshotRow;
import com.wushi.module.spider.core.SpiderFetchRequest;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.provider.SpiderProvider;

public interface CapitalFlowProvider extends SpiderProvider {

    SpiderResult<CapitalFlowDailySnapshotRow> fetchCapitalFlow(SpiderFetchRequest request);
}
