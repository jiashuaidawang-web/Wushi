package com.wushi.module.spider.checkpoint;

import java.time.LocalDate;

/**
 * 爬虫任务断点续传服务
 * 检查当天某任务是否已成功抓取, 避免重复执行
 */
public interface SpiderCheckpointService {

    /**
     * 判断某任务在指定交易日是否已成功完成
     */
    boolean isAlreadySucceeded(String taskCode, LocalDate tradeDate, String provider);

    /**
     * 记录任务开始执行
     */
    void markRunning(String taskCode, String taskName, LocalDate tradeDate, String provider);

    /**
     * 记录任务执行成功
     */
    void markSuccess(String taskCode, LocalDate tradeDate, String provider,
                     int successCount, int failCount, String checkpointValue);

    /**
     * 记录任务执行失败
     */
    void markFailed(String taskCode, LocalDate tradeDate, String provider, String errorMessage);
}
