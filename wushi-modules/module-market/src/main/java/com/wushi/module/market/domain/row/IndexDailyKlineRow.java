package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IndexDailyKlineRow(LocalDate tradeDate, String indexCode, String indexName, String indexType,
                                 BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                                 BigDecimal preClose, BigDecimal changePct, Long volume,
                                 BigDecimal amount, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.INDEX_DAILY_KLINE;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "index_code", "index_name", "index_type", "open", "high", "low", "close",
                "pre_close", "change_pct", "volume", "amount", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, indexCode, indexName, indexType, open, high, low, close, preClose, changePct,
                volume, amount, source};
    }
}
