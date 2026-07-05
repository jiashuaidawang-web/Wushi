package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StockMinuteKlineRow(LocalDate tradeDate, LocalDateTime minuteTime, String stockCode, String stockName,
                                  BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                                  Long volume, BigDecimal amount, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_MINUTE_KLINE;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "minute_time", "stock_code", "stock_name", "open", "high", "low", "close",
                "volume", "amount", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, minuteTime, stockCode, stockName, open, high, low, close, volume, amount, source};
    }
}
