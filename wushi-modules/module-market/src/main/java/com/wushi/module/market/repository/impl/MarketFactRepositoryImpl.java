package com.wushi.module.market.repository.impl;

import com.wushi.common.exception.BusinessException;
import com.wushi.module.market.domain.row.ClickHouseRow;
import com.wushi.module.market.enums.FactTable;
import com.wushi.module.market.repository.MarketFactRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class MarketFactRepositoryImpl implements MarketFactRepository {

    @Qualifier("clickHouseDataSource")
    private final DataSource clickHouseDataSource;

    public MarketFactRepositoryImpl(@Qualifier("clickHouseDataSource") DataSource clickHouseDataSource) {
        this.clickHouseDataSource = clickHouseDataSource;
    }

    private static final int BATCH_SIZE = 100;

    @Override
    public int saveBatch(List<? extends ClickHouseRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        ClickHouseRow first = rows.getFirst();
        validateSameShape(rows, first);

        String tableName = first.table().bareTableName();
        List<String> columns = first.columns();
        int cols = columns.size();
        String colList = String.join(", ", columns);

        int total = rows.size();
        int inserted = 0;

        for (int start = 0; start < total; start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, total);
            List<? extends ClickHouseRow> batch = rows.subList(start, end);

            StringBuilder sb = new StringBuilder("insert into ")
                    .append(tableName).append(" (").append(colList).append(") VALUES ");

            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) sb.append(", ");
                Object[] vals = batch.get(i).values();
                sb.append("(");
                for (int j = 0; j < cols; j++) {
                    if (j > 0) sb.append(", ");
                    sb.append(formatClickHouseValue(vals[j]));
                }
                sb.append(")");
            }

            try (Connection conn = clickHouseDataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute(sb.toString());
                inserted += batch.size();
                log.info("ClickHouse batch chunk: table={}, progress={}/{}", tableName, end, total);
            } catch (SQLException e) {
                log.warn("ClickHouse batch failed (sample3): table={}, rows={}, err={}", tableName, total, e.getMessage());
                throw new RuntimeException("ClickHouse batch failed: table=" + tableName + " rows=" + total, e);
            }
        }

        log.info("ClickHouse batch done: inserted={}, table={}", inserted, tableName);
        return inserted;
    }

    private String formatClickHouseValue(Object val) {
        if (val == null) return "NULL";
        if (val instanceof Number) return val.toString();
        if (val instanceof BigDecimal) return ((BigDecimal) val).toPlainString();
        if (val instanceof LocalDate) return "'" + val + "'";
        // LocalDateTime → 显式格式化为 'yyyy-MM-dd HH:mm:ss.SSS' (ClickHouse 接受毫秒)
        String str = (val instanceof LocalDateTime)
                ? ((LocalDateTime) val).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                : val.toString();
        return "'" + str.replace("'", "''") + "'";
    }

    @Override
    public int saveGrouped(List<? extends ClickHouseRow> rows) {
        if (rows == null || rows.isEmpty()) return 0;
        return rows.stream()
                .collect(Collectors.groupingBy(row -> row.table().name()))
                .values().stream().mapToInt(this::saveBatch).sum();
    }

    @Override
    public List<Map<String, Object>> findByTradeDate(FactTable table, LocalDate tradeDate) {
        if (!table.hasTradeDate()) throw new BusinessException("MARKET_TABLE_NO_TRADE_DATE", table.name());
        String sql = "select * from " + table.bareTableName() + " where " + table.tradeDateColumn() + " = '" + tradeDate + "' order by " + table.tradeDateColumn();
        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            List<Map<String, Object>> result = new ArrayList<>();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= cols; i++) row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                result.add(row);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("ClickHouse query failed: " + sql, e);
        }
    }

    @Override
    public List<Map<String, Object>> findByTradeDateAndCode(FactTable table, LocalDate tradeDate, String codeColumn, String code) {
        if (!table.hasTradeDate()) throw new BusinessException("MARKET_TABLE_NO_TRADE_DATE", table.name());
        validateCodeColumn(codeColumn);
        String sql = "select * from " + table.bareTableName() + " where " + table.tradeDateColumn() + " = '" + tradeDate + "' and " + codeColumn + " = '" + code + "' order by " + table.tradeDateColumn();
        try (Connection conn = clickHouseDataSource.getConnection();
             Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            List<Map<String, Object>> result = new ArrayList<>();
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= cols; i++) row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                result.add(row);
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("ClickHouse query failed: " + sql, e);
        }
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
