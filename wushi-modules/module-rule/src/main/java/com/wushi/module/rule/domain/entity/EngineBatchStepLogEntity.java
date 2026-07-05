package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("engine_batch_step_log")
public class EngineBatchStepLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String stepName;
    private Integer stepOrder;
    private String status;
    private Long affectedRows;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}
