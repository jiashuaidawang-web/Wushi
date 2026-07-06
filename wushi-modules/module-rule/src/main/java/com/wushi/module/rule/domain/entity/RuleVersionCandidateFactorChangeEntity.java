package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("rule_version_candidate_factor_change")
public class RuleVersionCandidateFactorChangeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String candidateId;
    private String engineType;
    private String factorCode;
    private String factorName;
    private BigDecimal baseWeight;
    private BigDecimal suggestedWeightDelta;
    private BigDecimal candidateWeight;
    private Integer sampleCount;
    private Integer hitCount;
    private Integer missCount;
    private Integer conflictHitCount;
    private BigDecimal hitRate;
    private BigDecimal avgContributionScore;
    private String suggestedAction;
    private String changeReason;
    private LocalDateTime createdAt;
}
