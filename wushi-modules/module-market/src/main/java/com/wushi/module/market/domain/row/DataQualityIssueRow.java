package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DataQualityIssueRow(LocalDate tradeDate, String issueId, String dataDomain, String tableName,
                                  String issueType, String severity, String impactLevel,
                                  List<String> impactPages, BigDecimal confidencePenalty,
                                  String description) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.DATA_QUALITY_ISSUE;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "issue_id", "data_domain", "table_name", "issue_type", "severity",
                "impact_level", "impact_pages", "confidence_penalty", "description");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, issueId, dataDomain, tableName, issueType, severity, impactLevel,
                impactPages, confidencePenalty, description};
    }
}
