package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.time.LocalDate;
import java.util.List;

public record TradingCalendarRow(LocalDate tradeDate, String market, Integer isTradeDay,
                                 LocalDate prevTradeDate, LocalDate nextTradeDate,
                                 Integer weekOfYear, String month) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.TRADING_CALENDAR;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "market", "is_trade_day", "prev_trade_date", "next_trade_date", "week_of_year", "month");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, market, isTradeDay, prevTradeDate, nextTradeDate, weekOfYear, month};
    }
}
