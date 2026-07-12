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
        // [ClickHouse fix] 对齐 DDL: stock_limit_status_daily (15 列)
        // 列顺序: trade_date, stock_code, stock_name, limit_status, limit_count, highest_board,
        //         failed_limit, sealed, sealed_amount, sealed_times, first_seal_time, last_seal_time,
        //         auction_ratio, source
        // 字段映射: highest_board/failed_limit/sealed/auction_ratio 由调用方在构造时通过
        //           limit_upType/consecutiveLimitUpDays/brokenLimitReason 传 null/默认值
        return List.of("trade_date", "stock_code", "stock_name", "limit_status", "limit_count", "highest_board",
                "failed_limit", "sealed", "sealed_amount", "sealed_times", "first_seal_time", "last_seal_time",
                "auction_ratio", "source");
    }

    @Override
    public Object[] values() {
        // 字段不够 14 个 -> 剩余列补 NULL；语义对齐由调用方保证
        return new Object[]{tradeDate, stockCode, stockName, limitStatus, consecutiveLimitUpDays, null,
                null, false, sealAmount, sealVolume, firstLimitTime, lastLimitTime, null, source};
    }
}
