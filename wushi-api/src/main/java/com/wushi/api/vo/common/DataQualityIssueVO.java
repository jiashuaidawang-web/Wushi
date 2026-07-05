package com.wushi.api.vo.common;

import java.math.BigDecimal;
import java.util.List;

public record DataQualityIssueVO(
        String issueId,
        String dataDomain,
        String tableName,
        String issueType,
        String severity,
        String impactLevel,
        List<String> impactPages,
        BigDecimal confidencePenalty,
        String description
) {
}
