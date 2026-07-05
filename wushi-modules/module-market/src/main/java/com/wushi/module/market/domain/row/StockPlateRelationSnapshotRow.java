package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockPlateRelationSnapshotRow(LocalDate tradeDate, String stockCode, String stockName,
                                            String plateCode, String plateName, String plateType,
                                            String relationSource, BigDecimal relationConfidence,
                                            Integer isCurrentBackfill, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_PLATE_RELATION_SNAPSHOT;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "stock_code", "stock_name", "plate_code", "plate_name", "plate_type",
                "relation_source", "relation_confidence", "is_current_backfill", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, stockCode, stockName, plateCode, plateName, plateType,
                relationSource, relationConfidence, isCurrentBackfill, source};
    }
}
