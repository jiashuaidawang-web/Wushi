package com.wushi.module.market.service.impl;

import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.repository.MarketFactRepository;
import com.wushi.module.market.service.MarketFactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketFactServiceImpl implements MarketFactService {

    private final MarketFactRepository marketFactRepository;

    @Override
    public int saveRows(List<? extends ClickHouseRow> rows) {
        return marketFactRepository.saveGrouped(rows);
    }

    @Override
    public int saveRow(ClickHouseRow row) {
        return marketFactRepository.saveBatch(List.of(row));
    }

    @Override
    public List<Map<String, Object>> findByTradeDate(FactTable table, LocalDate tradeDate) {
        return marketFactRepository.findByTradeDate(table, tradeDate);
    }

    @Override
    public List<Map<String, Object>> findStockDailyKline(LocalDate tradeDate, String stockCode) {
        return marketFactRepository.findByTradeDateAndCode(FactTable.STOCK_DAILY_KLINE, tradeDate, "stock_code", stockCode);
    }

    @Override
    public List<Map<String, Object>> findPlateDailyKline(LocalDate tradeDate, String plateCode) {
        return marketFactRepository.findByTradeDateAndCode(FactTable.STOCK_PLATE_DAILY_KLINE, tradeDate, "plate_code", plateCode);
    }

    @Override
    public List<Map<String, Object>> findStockPlateRelations(LocalDate tradeDate, String stockCode) {
        return marketFactRepository.findByTradeDateAndCode(FactTable.STOCK_PLATE_RELATION_SNAPSHOT, tradeDate, "stock_code", stockCode);
    }
}
