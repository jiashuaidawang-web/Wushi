package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_version_candidate_factor")
public class RuleVersionCandidateFactorEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String candidateId;
    private String engineType;
    private String factorCode;
    private String factorName;
    private BigDecimal currentWeight;
    private BigDecimal suggestedDelta;
    private BigDecimal suggestedWeight;
    private BigDecimal thresholdValue;
    private String thresholdOperator;
    private String evidenceType;
    private Integer sampleCount;
    private Integer hitCount;
    private Integer missCount;
    private Integer conflictHitCount;
    private BigDecimal hitRate;
    private BigDecimal avgContributionScore;
    private String suggestedAction;
    private String changeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
