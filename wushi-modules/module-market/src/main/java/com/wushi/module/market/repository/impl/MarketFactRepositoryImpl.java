package com.wushi.module.market.repository.impl;

import com.wushi.common.exception.BusinessException;
import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.repository.MarketFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MarketFactRepositoryImpl implements MarketFactRepository {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Override
    public int saveBatch(List<? extends ClickHouseRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        ClickHouseRow first = rows.getFirst();
        validateSameShape(rows, first);

        String tableName = first.table().tableName();
        if (tableName.contains(".")) tableName = tableName.substring(tableName.indexOf('.') + 1);
        String columns = String.join(", ", first.columns());
        String sqlPrefix = "insert into " + tableName + " (" + columns + ") values (";

        int inserted = 0;
        int failCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            Object[] vals = rows.get(i).values();
            StringBuilder sb = new StringBuilder(sqlPrefix);
            for (int j = 0; j < vals.length; j++) {
                if (j > 0) sb.append(", ");
                sb.append(formatValue(vals[j]));
            }
            sb.append(")");
            try {
                clickHouseJdbcTemplate.update(sb.toString());
                inserted++;
            } catch (Exception e) {
                failCount++;
                if (failCount <= 3) {
                    log.warn("ClickHouse row {} insert failed: {}", i, e.getMessage());
                }
            }
        }
        if (failCount > 0) {
            log.warn("ClickHouse batch: inserted={}, failed={}, total={}", inserted, failCount, rows.size());
        } else {
            log.info("ClickHouse batch: inserted={}", inserted);
        }

        if (inserted == 0 && !rows.isEmpty()) {
            throw new RuntimeException("ClickHouse batch insert: all " + rows.size() + " rows failed");
        }
        return inserted;
    }

    private String formatValue(Object val) {
        if (val == null) return "NULL";
        if (val instanceof Number) return val.toString();
        if (val instanceof LocalDate) return "'" + val + "'";
        return "'" + val.toString().replace("'", "''") + "'";
    }

    @Override
    public int saveGrouped(List<? extends ClickHouseRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        return rows.stream()
            .collect(Collectors.groupingBy(row -> row.table().name() + "|" + String.join(",", row.columns())))
            .values().stream().mapToInt(this::saveBatch).sum();
    }

    @Override
    public List<Map<String, Object>> findByTradeDate(FactTable table, LocalDate tradeDate) {
        if (!table.hasTradeDate()) throw new BusinessException("MARKET_TABLE_NO_TRADE_DATE", table.name());
        String sql = "select * from " + table.tableName() + " where " + table.tradeDateColumn() + " = ? order by " + table.tradeDateColumn();
        return clickHouseJdbcTemplate.queryForList(sql, tradeDate);
    }

    @Override
    public List<Map<String, Object>> findByTradeDateAndCode(FactTable table, LocalDate tradeDate, String codeColumn, String code) {
        if (!table.hasTradeDate()) throw new BusinessException("MARKET_TABLE_NO_TRADE_DATE", table.name());
        validateCodeColumn(codeColumn);
        String sql = "select * from " + table.tableName() + " where " + table.tradeDateColumn() + " = ? and " + codeColumn + " = ? order by " + table.tradeDateColumn();
        return clickHouseJdbcTemplate.queryForList(sql, tradeDate, code);
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
