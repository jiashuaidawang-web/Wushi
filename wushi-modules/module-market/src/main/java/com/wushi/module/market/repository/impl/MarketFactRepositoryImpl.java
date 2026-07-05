package com.wushi.module.market.repository.impl;

import com.wushi.common.exception.BusinessException;
import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.repository.MarketFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MarketFactRepositoryImpl implements MarketFactRepository {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public int saveBatch(List<? extends ClickHouseRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        ClickHouseRow first = rows.getFirst();
        validateSameShape(rows, first);
        String sql = buildInsertSql(first);
        int[] result = clickHouseJdbcTemplate.batchUpdate(sql, rows.stream().map(ClickHouseRow::values).toList());
        int affected = 0;
        for (int count : result) {
            affected += Math.max(count, 0);
        }
        return affected;
    }

    @Override
    public int saveGrouped(List<? extends ClickHouseRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        return rows.stream()
                .collect(Collectors.groupingBy(row -> row.table().name() + "|" + String.join(",", row.columns())))
                .values()
                .stream()
                .mapToInt(this::saveBatch)
                .sum();
    }

    @Override
    public List<Map<String, Object>> findByTradeDate(FactTable table, LocalDate tradeDate) {
        if (!table.hasTradeDate()) {
            throw new BusinessException("MARKET_TABLE_NO_TRADE_DATE", table.name() + " does not have trade date column");
        }
        String sql = "select * from " + table.tableName() + " where " + table.tradeDateColumn() + " = ? order by " + table.tradeDateColumn();
        return clickHouseJdbcTemplate.queryForList(sql, tradeDate);
    }

    @Override
    public List<Map<String, Object>> findByTradeDateAndCode(FactTable table, LocalDate tradeDate, String codeColumn, String code) {
        if (!table.hasTradeDate()) {
            throw new BusinessException("MARKET_TABLE_NO_TRADE_DATE", table.name() + " does not have trade date column");
        }
        validateCodeColumn(codeColumn);
        String sql = "select * from " + table.tableName()
                + " where " + table.tradeDateColumn() + " = ? and " + codeColumn + " = ?"
                + " order by " + table.tradeDateColumn();
        return clickHouseJdbcTemplate.queryForList(sql, tradeDate, code);
    }

    private String buildInsertSql(ClickHouseRow row) {
        String columns = String.join(", ", row.columns());
        String placeholders = row.columns().stream().map(column -> "?").collect(Collectors.joining(", "));
        return "insert into " + row.table().tableName() + " (" + columns + ") values (" + placeholders + ")";
    }

    private void validateSameShape(List<? extends ClickHouseRow> rows, ClickHouseRow first) {
        for (ClickHouseRow row : rows) {
            if (row.table() != first.table() || !Objects.equals(row.columns(), first.columns())) {
                throw new BusinessException("MARKET_ROW_SHAPE_MISMATCH", "rows in one batch must target same table and columns");
            }
        }
    }

    private void validateCodeColumn(String codeColumn) {
        if (!codeColumn.matches("[a-zA-Z0-9_]+")) {
            throw new BusinessException("MARKET_INVALID_CODE_COLUMN", "invalid code column: " + codeColumn);
        }
    }
}
