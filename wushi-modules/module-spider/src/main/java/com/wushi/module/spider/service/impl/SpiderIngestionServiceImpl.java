package com.wushi.module.spider.service.impl;

import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.service.MarketFactService;
import com.wushi.module.spider.core.SpiderResult;
import com.wushi.module.spider.core.SpiderWriteResult;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import com.wushi.module.spider.service.SpiderIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpiderIngestionServiceImpl implements SpiderIngestionService {

    private final MarketFactService marketFactService;

    @Override
    public SpiderWriteResult ingest(SpiderResult<? extends ClickHouseRow> spiderResult) {
        if (spiderResult == null) {
            return SpiderWriteResult.builder()
                    .status(SpiderTaskStatus.FAILED.name())
                    .errorMessage("spider result is null")
                    .build();
        }
        List<? extends ClickHouseRow> rows = spiderResult.getRows();
        int inserted = rows == null || rows.isEmpty() ? 0 : marketFactService.saveRows(rows);
        return SpiderWriteResult.builder()
                .taskCode(spiderResult.getTaskCode())
                .provider(spiderResult.getProvider())
                .fetchedCount(spiderResult.getFetchedCount())
                .insertedCount(inserted)
                .failedCount(spiderResult.getFailedCount())
                .status(spiderResult.getStatus().name())
                .errorMessage(spiderResult.getErrorMessage())
                .build();
    }
}
