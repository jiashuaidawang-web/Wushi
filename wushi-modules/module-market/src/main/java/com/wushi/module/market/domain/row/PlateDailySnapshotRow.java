package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlateDailySnapshotRow(LocalDate tradeDate, String plateCode, String plateName, String plateType,
                                    Integer stockCount, Integer upCount, Integer downCount, Integer limitUpCount,
                                    Integer brokenLimitCount, Integer limitDownCount,
                                    BigDecimal avgChangePct, BigDecimal medianChangePct,
                                    BigDecimal amount, BigDecimal mainNetInflow,
                                    String leaderStockCode, String leaderStockName,
                                    BigDecimal ladderIntegrityScore, BigDecimal sustainabilityScore,
                                    String relationQualityLevel) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.PLATE_DAILY_SNAPSHOT;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "plate_code", "plate_name", "plate_type", "stock_count", "up_count",
                "down_count", "limit_up_count", "broken_limit_count", "limit_down_count", "avg_change_pct",
                "median_change_pct", "amount", "main_net_inflow", "leader_stock_code", "leader_stock_name",
                "ladder_integrity_score", "sustainability_score", "relation_quality_level");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, plateCode, plateName, plateType, stockCount, upCount, downCount, limitUpCount,
                brokenLimitCount, limitDownCount, avgChangePct, medianChangePct, amount, mainNetInflow,
                leaderStockCode, leaderStockName, ladderIntegrityScore, sustainabilityScore, relationQualityLevel};
    }
}
