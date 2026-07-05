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
        return List.of("trade_date", "up_count", "down_count", "flat_count", "limit_up_count", "limit_down_count",
                "broken_limit_count", "high_open_count", "low_open_count", "above_ma5_count", "above_ma10_count",
                "above_ma20_count", "money_effect_score", "loss_effect_score");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, upCount, downCount, flatCount, limitUpCount, limitDownCount, brokenLimitCount,
                highOpenCount, lowOpenCount, aboveMa5Count, aboveMa10Count, aboveMa20Count,
                moneyEffectScore, lossEffectScore};
    }
}
