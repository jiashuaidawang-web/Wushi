package com.wushi.module.market.common;

import com.wushi.module.market.enums.FactTable;
import com.wushi.common.enums.DataQualityLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 数据覆盖率检查器
 * 查询 ClickHouse 当天数据量与历史均值对比, 返回覆盖率及质量级别
 * <p>
 * 级别定义:
 * - L1 (HIGH): coverage >= 80%
 * - L2 (MEDIUM): coverage >= 50%
 * - L3 (LOW): coverage < 50%
 */
@Slf4j
@Component
public class DataCoverageChecker {

    /**
     * 历史均值计算窗口: 取样最近 N 个有数据的交易日
     */
    private static final int HISTORICAL_WINDOW_DAYS = 20;

    /**
     * L1 阈值: 覆盖率 >= 80% 视为高置信
     */
    private static final double THRESHOLD_L1 = 0.80;

    /**
     * L2 阈值: 覆盖率 >= 50% 视为可用(降级)
     */
    private static final double THRESHOLD_L2 = 0.50;

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    public DataCoverageChecker(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
    }

    /**
     * 检查指定表在指定交易日的数据覆盖率
     *
     * @param table     事实表
     * @param tradeDate 交易日
     * @return 覆盖率 (0.0 ~ 1.0), 历史无数据时返回 1.0 (避免误杀)
     */
    public double checkCoverage(FactTable table, LocalDate tradeDate) {
        if (table == null || !table.hasTradeDate() || tradeDate == null) {
            return 1.0;
        }
        String tableName = table.tableName();
        String tradeDateColumn = table.tradeDateColumn();

        try {
            int todayCount = queryCount(tableName, tradeDateColumn, tradeDate);
            if (todayCount <= 0) {
                log.warn("数据覆盖率为0: table={}, tradeDate={}", tableName, tradeDate);
                return 0.0;
            }
            double historicalAvg = queryHistoricalAverage(tableName, tradeDateColumn, tradeDate);
            if (historicalAvg <= 0) {
                // 历史无数据, Today 有数据, 视为完全覆盖
                return 1.0;
            }
            double coverage = todayCount / historicalAvg;
            log.info("数据覆盖率: table={}, tradeDate={}, today={}, avg={}, coverage={}",
                    tableName, tradeDate, todayCount, String.format("%.1f", historicalAvg),
                    String.format("%.2f", coverage));
            return Math.min(coverage, 1.0);
        } catch (Exception e) {
            log.error("覆盖率检查失败: table={}, tradeDate={}, error={}", tableName, tradeDate, e.getMessage());
            // 检查失败时不阻塞引擎, 视为完全覆盖
            return 1.0;
        }
    }

    /**
     * 检查覆盖率并映射为 DataQualityLevel
     */
    public DataQualityLevel checkLevel(FactTable table, LocalDate tradeDate) {
        double coverage = checkCoverage(table, tradeDate);
        if (coverage >= THRESHOLD_L1) {
            return DataQualityLevel.HIGH;
        } else if (coverage >= THRESHOLD_L2) {
            return DataQualityLevel.MEDIUM;
        } else {
            return DataQualityLevel.LOW;
        }
    }

    private int queryCount(String tableName, String tradeDateColumn, LocalDate tradeDate) {
        String sql = String.format("SELECT count() AS cnt FROM %s WHERE %s = ?", tableName, tradeDateColumn);
        Integer count = clickHouseJdbcTemplate.queryForObject(sql, Integer.class, tradeDate.toString());
        return count == null ? 0 : count;
    }

    /**
     * 查询历史均值: tradeDate 前 HISTORICAL_WINDOW_DAYS 个自然日内有数据的交易日的平均 count
     */
    private double queryHistoricalAverage(String tableName, String tradeDateColumn, LocalDate tradeDate) {
        // 取样 tradeDate 前 60 个自然日内有数据的交易日, 最多取 HISTORICAL_WINDOW_DAYS 条
        LocalDate startDate = tradeDate.minusDays(60);
        String sql = String.format(
                "SELECT toInt32(avg(daily_cnt)) FROM (" +
                "  SELECT %s AS d, count() AS daily_cnt FROM %s " +
                "  WHERE %s >= ? AND %s < ? GROUP BY %s ORDER BY d DESC LIMIT %d" +
                ")",
                tradeDateColumn, tableName, tradeDateColumn, tradeDateColumn, tradeDateColumn, HISTORICAL_WINDOW_DAYS);
        Integer avg = clickHouseJdbcTemplate.queryForObject(sql, Integer.class,
                startDate.toString(), tradeDate.toString());
        return avg == null ? 0.0 : avg.doubleValue();
    }
}
