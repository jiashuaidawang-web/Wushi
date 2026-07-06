package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rule_version_candidate")
public class RuleVersionCandidateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String candidateId;
    private String baseRuleVersion;
    private String targetRuleVersion;
    private String engineType;
    private String status;
    private LocalDate statDate;
    private Integer factorChangeCount;
    private Integer sampleCount;
    private BigDecimal totalAbsDelta;
    private String reasonSummary;
    private String riskSummary;
    private String generatedBy;
    private String approvedBy;
    private String approvalComment;
    private LocalDateTime approvedAt;
    private LocalDateTime effectiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
