package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.util.List;

public record StockPlateDimensionRow(String plateCode, String plateName, String plateType,
                                     String parentPlateCode, String status, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_PLATE_DIMENSION;
    }

    @Override
    public List<String> columns() {
        return List.of("plate_code", "plate_name", "plate_type", "parent_plate_code", "status", "source", "updated_at");
    }

    @Override
    public Object[] values() {
        return new Object[]{plateCode, plateName, plateType, parentPlateCode, status, source, java.time.LocalDateTime.now()};
    }
}
