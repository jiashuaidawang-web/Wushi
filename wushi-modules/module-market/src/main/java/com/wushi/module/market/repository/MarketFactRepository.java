package com.wushi.module.market.repository;

import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.enums.FactTable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MarketFactRepository {

    int saveBatch(List<? extends ClickHouseRow> rows);

    int saveGrouped(List<? extends ClickHouseRow> rows);

    List<Map<String, Object>> findByTradeDate(FactTable table, LocalDate tradeDate);

    List<Map<String, Object>> findByTradeDateAndCode(FactTable table, LocalDate tradeDate, String codeColumn, String code);

    /**
     * 金融级对账:验证 ClickHouse 某表某日是否有数据
     */
    int countByTradeDate(String tableName, LocalDate tradeDate);
}
