package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("data_quality_impact_config")
public class DataQualityImpactConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String dataDomain;
    private String tableName;
    private String missingField;
    private String impactPages;
    private BigDecimal confidencePenalty;
    private String impactDesc;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
