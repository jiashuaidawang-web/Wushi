package com.wushi.module.market.domain.row;

import com.wushi.module.market.enums.FactTable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CapitalFlowDailySnapshotRow(LocalDate tradeDate, String targetType, String targetCode, String targetName,
                                          BigDecimal mainNetInflow, BigDecimal superLargeNetInflow,
                                          BigDecimal largeNetInflow, BigDecimal mediumNetInflow,
                                          BigDecimal smallNetInflow, String source) implements ClickHouseRow {

    @Override
    public FactTable table() {
        return FactTable.CAPITAL_FLOW_DAILY_SNAPSHOT;
    }

    @Override
    public List<String> columns() {
        return List.of("trade_date", "target_type", "target_code", "target_name", "main_net_inflow",
                "super_large_net_inflow", "large_net_inflow", "medium_net_inflow", "small_net_inflow", "source");
    }

    @Override
    public Object[] values() {
        return new Object[]{tradeDate, targetType, targetCode, targetName, mainNetInflow, superLargeNetInflow,
                largeNetInflow, mediumNetInflow, smallNetInflow, source};
    }
}
