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
        // [ClickHouse fix] 对齐 DDL (10 列,无 created_at)
        return List.of("trade_date", "plate_code", "plate_name", "plate_type", "stock_code", "stock_name",
                "weight", "is_leader", "leader_rank", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, plateCode, plateName, plateType, stockCode, stockName, null, null, null, source};
    }
}
