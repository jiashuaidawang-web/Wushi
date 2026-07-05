package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_factor_weight")
public class RuleFactorWeightEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleVersion;
    private String engineType;
    private String factorCode;
    private BigDecimal weight;
    private BigDecimal thresholdValue;
    private String thresholdOperator;
    private String evidenceType;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
