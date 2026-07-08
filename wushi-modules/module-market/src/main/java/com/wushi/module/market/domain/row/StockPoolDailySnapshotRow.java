package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.time.LocalDate;
import java.util.List;

public record StockPoolDailySnapshotRow(LocalDate tradeDate, String stockCode, String stockName,
                                        String market, String board, Integer isSt,
                                        Integer isNewStock, LocalDate listDate,
                                        String suspendStatus, String poolType,
                                        String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_POOL_DAILY_SNAPSHOT;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "stock_code", "stock_name", "market", "board", "is_st",
                "is_new_stock", "list_date", "suspend_status", "pool_type", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, stockCode, stockName, market, board, isSt, isNewStock, listDate, suspendStatus, poolType, source};
    }
}
