package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("engine_batch_run_log")
public class EngineBatchRunLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private LocalDate tradeDate;
    private LocalDate asOfDate;
    private String judgementMode;
    private String runMode;
    private String ruleVersion;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
