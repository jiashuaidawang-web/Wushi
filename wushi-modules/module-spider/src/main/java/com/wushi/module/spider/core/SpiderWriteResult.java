package com.wushi.module.spider.core;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpiderWriteResult {

    private String taskCode;
    private String provider;
    private int fetchedCount;
    private int insertedCount;
    private int failedCount;
    private String status;
    private String errorMessage;
}
