package com.wushi.module.market.service;

import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.enums.FactTable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MarketFactService {

    int saveRows(List<? extends ClickHouseRow> rows);

    int saveRow(ClickHouseRow row);

    List<Map<String, Object>> findByTradeDate(FactTable table, LocalDate tradeDate);

    List<Map<String, Object>> findStockDailyKline(LocalDate tradeDate, String stockCode);

    List<Map<String, Object>> findPlateDailyKline(LocalDate tradeDate, String plateCode);

    List<Map<String, Object>> findStockPlateRelations(LocalDate tradeDate, String stockCode);
}
