package com.wushi.common.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DataQualityIssue {

    private String issueId;
    private String dataDomain;
    private String tableName;
    private String issueType;
    private String severity;
    private String impactLevel;
    private List<String> impactPages;
    private BigDecimal confidencePenalty;
    private String description;
}
