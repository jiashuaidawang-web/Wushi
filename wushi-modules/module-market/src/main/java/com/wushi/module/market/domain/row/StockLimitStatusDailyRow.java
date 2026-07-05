package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StockLimitStatusDailyRow(LocalDate tradeDate, String stockCode, String stockName,
                                       String limitStatus, String limitUpType, Integer consecutiveLimitUpDays,
                                       LocalDateTime firstLimitTime, LocalDateTime lastLimitTime,
                                       Integer openLimitTimes, BigDecimal sealAmount, Long sealVolume,
                                       String brokenLimitReason, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.STOCK_LIMIT_STATUS_DAILY;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "stock_code", "stock_name", "limit_status", "limit_up_type",
                "consecutive_limit_up_days", "first_limit_time", "last_limit_time", "open_limit_times",
                "seal_amount", "seal_volume", "broken_limit_reason", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, stockCode, stockName, limitStatus, limitUpType, consecutiveLimitUpDays,
                firstLimitTime, lastLimitTime, openLimitTimes, sealAmount, sealVolume, brokenLimitReason, source};
    }
}
