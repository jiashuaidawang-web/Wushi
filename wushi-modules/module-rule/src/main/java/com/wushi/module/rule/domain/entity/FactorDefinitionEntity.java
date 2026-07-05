package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("factor_definition")
public class FactorDefinitionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String factorCode;
    private String factorName;
    private String engineType;
    private String factorDirection;
    private String factorDesc;
    private String sourceTable;
    private String valueType;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
