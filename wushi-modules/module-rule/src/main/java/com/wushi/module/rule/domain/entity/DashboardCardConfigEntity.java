package com.wushi.module.rule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dashboard_card_config")
public class DashboardCardConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String pageCode;
    private String cardCode;
    private String cardName;
    private String cardType;
    private String dataApi;
    private Integer displayOrder;
    private String requiredFields;
    private String thoughtMapping;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
