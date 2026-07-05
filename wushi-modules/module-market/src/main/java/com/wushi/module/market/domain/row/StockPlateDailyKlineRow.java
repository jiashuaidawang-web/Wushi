package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockPlateDailyKlineRow(LocalDate tradeDate, String plateCode, String plateName, String plateType,
                                      BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                                      BigDecimal preClose, BigDecimal changePct, Long volume, BigDecimal amount,
                                      BigDecimal turnoverRate, BigDecimal mainNetInflow,
                                      String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_PLATE_DAILY_KLINE;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "plate_code", "plate_name", "plate_type", "open", "high", "low", "close",
                "pre_close", "change_pct", "volume", "amount", "turnover_rate", "main_net_inflow", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, plateCode, plateName, plateType, open, high, low, close, preClose,
                changePct, volume, amount, turnoverRate, mainNetInflow, source};
    }
}
