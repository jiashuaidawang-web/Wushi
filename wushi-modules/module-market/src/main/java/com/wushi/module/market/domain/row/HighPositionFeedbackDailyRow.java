package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record HighPositionFeedbackDailyRow(LocalDate tradeDate, String stockCode, String stockName,
                                           Integer positionLevel, String feedbackType,
                                           BigDecimal changePct, BigDecimal drawdownPct,
                                           BigDecimal impactScore, List<String> relatedPlateCodes) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.HIGH_POSITION_FEEDBACK_DAILY;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "stock_code", "stock_name", "position_level", "feedback_type",
                "change_pct", "drawdown_pct", "impact_score", "related_plate_codes", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, stockCode, stockName, positionLevel, feedbackType, changePct,
                drawdownPct, impactScore, relatedPlateCodes, "MARKET_DATA"};
    }
}
