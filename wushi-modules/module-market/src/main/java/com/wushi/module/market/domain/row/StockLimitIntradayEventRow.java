package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StockLimitIntradayEventRow(LocalDate tradeDate, LocalDateTime eventTime,
                                         String stockCode, String stockName, String eventType,
                                         BigDecimal price, BigDecimal sealAmount,
                                         Integer eventSequence, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_LIMIT_INTRADAY_EVENT;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "stock_code", "stock_name", "event_type", "event_time", "event_price",
                "event_amount", "interval_seconds", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, stockCode, stockName, eventType, eventTime, price, sealAmount, null, source};
    }
}
