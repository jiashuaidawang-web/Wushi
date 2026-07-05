package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.time.LocalDate;
import java.util.List;

public record LimitLadderDailySnapshotRow(LocalDate tradeDate, Integer ladderLevel, Integer stockCount,
                                          List<String> stockCodes, List<String> stockNames,
                                          String strongestStockCode, String strongestStockName) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.LIMIT_LADDER_DAILY_SNAPSHOT;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "ladder_level", "stock_count", "stock_codes", "stock_names",
                "strongest_stock_code", "strongest_stock_name");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, ladderLevel, stockCount, stockCodes, stockNames, strongestStockCode, strongestStockName};
    }
}
