package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rule_version")
public class RuleVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleVersion;
    private String ruleName;
    private String engineType;
    private String status;
    private String description;
    private String sourceRuleVersion;
    private LocalDate candidateStatDate;
    private LocalDate effectiveDate;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String approvalRemark;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
