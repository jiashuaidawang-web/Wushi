package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MarketBreadthDailySnapshotRow(LocalDate tradeDate, Integer upCount, Integer downCount, Integer flatCount,
                                            Integer limitUpCount, Integer limitDownCount, Integer brokenLimitCount,
                                            Integer highOpenCount, Integer lowOpenCount,
                                            Integer aboveMa5Count, Integer aboveMa10Count, Integer aboveMa20Count,
                                            BigDecimal moneyEffectScore, BigDecimal lossEffectScore) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.MARKET_BREADTH_DAILY_SNAPSHOT;
    }

    @Override
    public List<String> columns() {
        // [ClickHouse fix] 对齐 DDL (22 列)。Java record 字段不够时由 values() 补 NULL。
        return List.of("trade_date", "total_stocks", "up_count", "down_count", "flat_count", "limit_up_count",
                "limit_down_count", "broken_limit_count", "up_down_ratio", "limit_up_ratio", "limit_down_ratio",
                "yest_limit_up_count", "yest_limit_up_now_up", "yest_limit_up_now_down", "yest_limit_up_now_limit_up",
                "yest_limit_up_feedback", "jdp_ratio", "sentiment_score", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, null, upCount, downCount, flatCount, limitUpCount, limitDownCount, brokenLimitCount,
                null, null, null, null, null, null, null, null, null, null};
    }
}
