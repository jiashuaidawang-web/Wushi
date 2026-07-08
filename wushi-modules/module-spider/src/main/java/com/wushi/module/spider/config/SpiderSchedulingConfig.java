package com.wushi.module.spider.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 爬虫模块异步+定时任务配置
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SpiderSchedulingConfig {
}
