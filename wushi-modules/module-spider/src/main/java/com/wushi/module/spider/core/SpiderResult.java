package com.wushi.module.spider.core;

import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.spider.enums.SpiderTaskStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SpiderResult<T extends ClickHouseRow> {

    private String taskCode;
    private String provider;
    private SpiderTaskStatus status;
    private List<T> rows;
    private int fetchedCount;
    private int successCount;
    private int failedCount;
    private String checkpointValue;
    private String errorMessage;

    public boolean successful() {
        return status == SpiderTaskStatus.SUCCESS || status == SpiderTaskStatus.PARTIAL;
    }
}
