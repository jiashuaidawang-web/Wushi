package com.wushi.module.spider.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("spider_task_checkpoint")
public class SpiderTaskCheckpointEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskCode;
    private String taskName;
    private LocalDate tradeDate;
    private String provider;
    private String checkpointValue;
    private String status;
    private Integer successCount;
    private Integer failCount;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
