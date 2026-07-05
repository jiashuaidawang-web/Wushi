package com.wushi.module.spider.service;

import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.core.SpiderWriteResult;

public interface SpiderIngestionService {

    SpiderWriteResult ingest(SpiderResult<? extends ClickHouseRow> spiderResult);
}
