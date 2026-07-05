package com.wushi.module.rule.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardCardConfig {

    private String pageCode;
    private String cardCode;
    private String cardName;
    private String cardType;
    private String dataApi;
    private Integer displayOrder;
    private String requiredFields;
    private String thoughtMapping;
}
