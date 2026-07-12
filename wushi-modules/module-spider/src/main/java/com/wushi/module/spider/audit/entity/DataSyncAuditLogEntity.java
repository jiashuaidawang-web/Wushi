package com.wushi.module.spider.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("data_sync_audit_log")
public class DataSyncAuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String auditId;
    private String taskCode;
    private LocalDate tradeDate;
    private String provider;
    private String targetTable;
    private String syncStatus;
    private Integer fetchedCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer failedCount;
    private LocalDateTime dataStartTime;
    private LocalDateTime dataEndTime;
    private String errorMessage;
}
