package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockDailyKlineRow(LocalDate tradeDate, String stockCode, String stockName,
                                 BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                                 BigDecimal preClose, BigDecimal changeAmount, BigDecimal changePct,
                                 Long volume, BigDecimal amount, BigDecimal turnoverRate, BigDecimal amplitude,
                                 BigDecimal limitUpPrice, BigDecimal limitDownPrice,
                                 BigDecimal totalMarketValue, BigDecimal floatMarketValue,
                                 String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_DAILY_KLINE;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "stock_code", "stock_name", "open", "high", "low", "close",
                "pre_close", "change_amount", "change_pct", "volume", "amount", "turnover_rate",
                "amplitude", "limit_up_price", "limit_down_price", "total_market_value",
                "float_market_value", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, stockCode, stockName, open, high, low, close, preClose, changeAmount,
                changePct, volume, amount, turnoverRate, amplitude, limitUpPrice, limitDownPrice,
                totalMarketValue, floatMarketValue, source};
    }
}
