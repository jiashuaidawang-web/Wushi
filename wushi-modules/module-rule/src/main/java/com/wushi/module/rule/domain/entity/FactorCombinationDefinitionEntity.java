package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("factor_combination_definition")
public class FactorCombinationDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String combinationCode;
    private String combinationName;
    private String engineType;
    private String ruleVersion;
    private String factorCodes;
    private String conditionExpression;
    private String expectedMeaning;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
