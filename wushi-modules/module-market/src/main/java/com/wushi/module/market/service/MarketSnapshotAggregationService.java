package com.wushi.module.market.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 市场快照聚合服务
 * 从事实表聚合计算 plate_daily_snapshot 和 market_breadth_daily_snapshot
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketSnapshotAggregationService {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    @Qualifier("clickHouseDataSource")
    private final DataSource clickHouseDataSource;

    /**
     * 聚合板块每日快照
     */
    public int aggregatePlateDailySnapshot(LocalDate tradeDate) {
        log.info("===== 开始聚合 plate_daily_snapshot: tradeDate={} =====", tradeDate);
        log.info("[DEBUG] ClickHouse DataSource URL: {}", getDataSourceUrl());

        try {
            String deleteSql = "ALTER TABLE wushi.plate_daily_snapshot DELETE WHERE trade_date = ?";
            log.info("[DEBUG] 执行SQL: {}", deleteSql);
            clickHouseJdbcTemplate.update(deleteSql, tradeDate.toString());
            log.info("[DEBUG] plate_daily_snapshot 旧数据清理完成");
        } catch (Exception e) {
            log.error("[DEBUG] plate_daily_snapshot DELETE 失败: {}", e.getMessage());
            throw e;
        }

        String sql = """
            INSERT INTO wushi.plate_daily_snapshot
            (trade_date, plate_code, plate_name, plate_type, stock_count,
             up_count, down_count, limit_up_count, broken_limit_count, limit_down_count,
             avg_change_pct, median_change_pct, amount, main_net_inflow,
             leader_stock_code, leader_stock_name,
             ladder_integrity_score, sustainability_score, relation_quality_level)
            SELECT
                k.trade_date,
                r.plate_code,
                d.plate_name,
                d.plate_type,
                count(DISTINCT k.stock_code) AS stock_count,
                countIf(k.change_pct > 0) AS up_count,
                countIf(k.change_pct < 0) AS down_count,
                countIf(l.limit_status = 'LIMIT_UP') AS limit_up_count,
                countIf(l.limit_status = 'BROKEN_LIMIT') AS broken_limit_count,
                countIf(l.limit_status = 'LIMIT_DOWN') AS limit_down_count,
                avg(k.change_pct) AS avg_change_pct,
                median(k.change_pct) AS median_change_pct,
                sum(k.amount) AS amount,
                sum(cf.main_net_inflow) AS main_net_inflow,
                argMax(k.stock_code, k.change_pct) AS leader_stock_code,
                argMax(k.stock_name, k.change_pct) AS leader_stock_name,
                round(countIf(l.limit_status = 'LIMIT_UP') / greatest(count(DISTINCT k.stock_code), 1), 4) AS ladder_integrity_score,
                round(countIf(k.change_pct > 0) / greatest(count(DISTINCT k.stock_code), 1), 4) AS sustainability_score,
                if(count(DISTINCT k.stock_code) > 50, 'HIGH', if(count(DISTINCT k.stock_code) > 20, 'MEDIUM', 'LOW')) AS relation_quality_level
            FROM wushi.stock_daily_kline k
            INNER JOIN wushi.stock_plate_relation_snapshot r
                ON k.stock_code = r.stock_code AND k.trade_date = r.trade_date
            LEFT JOIN wushi.stock_plate_dimension d
                ON r.plate_code = d.plate_code
            LEFT JOIN wushi.stock_limit_status_daily l
                ON k.stock_code = l.stock_code AND k.trade_date = l.trade_date
            LEFT JOIN wushi.capital_flow_daily_snapshot cf
                ON k.stock_code = cf.target_code AND k.trade_date = cf.trade_date AND cf.target_type = 'STOCK'
            WHERE k.trade_date = ?
            GROUP BY k.trade_date, r.plate_code, d.plate_name, d.plate_type
            """;

        log.info("[DEBUG] 执行 plate_daily_snapshot INSERT 聚合...");
        int inserted = clickHouseJdbcTemplate.update(sql, tradeDate.toString());
        log.info("===== plate_daily_snapshot 聚合完成: tradeDate={}, inserted={} =====", tradeDate, inserted);
        return inserted;
    }

    /**
     * 聚合市场宽度每日快照
     */
    public int aggregateMarketBreadthDailySnapshot(LocalDate tradeDate) {
        log.info("===== 开始聚合 market_breadth_daily_snapshot: tradeDate={} =====", tradeDate);
        log.info("[DEBUG] ClickHouse DataSource URL: {}", getDataSourceUrl());

        try {
            String deleteSql = "ALTER TABLE wushi.market_breadth_daily_snapshot DELETE WHERE trade_date = ?";
            log.info("[DEBUG] 执行SQL: {}", deleteSql);
            clickHouseJdbcTemplate.update(deleteSql, tradeDate.toString());
            log.info("[DEBUG] market_breadth_daily_snapshot 旧数据清理完成");
        } catch (Exception e) {
            log.error("[DEBUG] market_breadth_daily_snapshot DELETE 失败: {}", e.getMessage());
            throw e;
        }

        // 使用窗口函数计算 MA5/MA10/MA20
        String sql = """
            INSERT INTO wushi.market_breadth_daily_snapshot
            (trade_date, up_count, down_count, flat_count,
             limit_up_count, limit_down_count, broken_limit_count,
             high_open_count, low_open_count,
             above_ma5_count, above_ma10_count, above_ma20_count,
             money_effect_score, loss_effect_score)
            SELECT
                trade_date,
                countIf(change_pct > 0) AS up_count,
                countIf(change_pct < 0) AS down_count,
                countIf(change_pct = 0) AS flat_count,
                countIf(limit_status = 'LIMIT_UP') AS limit_up_count,
                countIf(limit_status = 'LIMIT_DOWN') AS limit_down_count,
                countIf(limit_status = 'BROKEN_LIMIT') AS broken_limit_count,
                countIf(open > pre_close) AS high_open_count,
                countIf(open < pre_close) AS low_open_count,
                countIf(close > ma5) AS above_ma5_count,
                countIf(close > ma10) AS above_ma10_count,
                countIf(close > ma20) AS above_ma20_count,
                greatest(0, countIf(limit_status = 'LIMIT_UP') + countIf(change_pct > 0) * 0.1
                    - countIf(change_pct < 0) * 0.1) AS money_effect_score,
                greatest(0, countIf(change_pct < 0) * 0.1 + countIf(limit_status = 'LIMIT_DOWN') * 0.2
                    - countIf(change_pct > 0) * 0.05) AS loss_effect_score
            FROM (
                SELECT
                    k.trade_date, k.stock_code, k.change_pct, k.open, k.pre_close, k.close,
                    l.limit_status,
                    avg(k.close) OVER (PARTITION BY k.stock_code ORDER BY k.trade_date ROWS BETWEEN 4 PRECEDING AND CURRENT ROW) AS ma5,
                    avg(k.close) OVER (PARTITION BY k.stock_code ORDER BY k.trade_date ROWS BETWEEN 9 PRECEDING AND CURRENT ROW) AS ma10,
                    avg(k.close) OVER (PARTITION BY k.stock_code ORDER BY k.trade_date ROWS BETWEEN 19 PRECEDING AND CURRENT ROW) AS ma20
                FROM wushi.stock_daily_kline k
                LEFT JOIN wushi.stock_limit_status_daily l
                    ON k.stock_code = l.stock_code AND k.trade_date = l.trade_date
                WHERE k.trade_date = ?
            )
            """;

        log.info("[DEBUG] 执行 market_breadth_daily_snapshot INSERT 聚合...");
        int inserted = clickHouseJdbcTemplate.update(sql, tradeDate.toString());
        log.info("===== market_breadth_daily_snapshot 聚合完成: tradeDate={}, inserted={} =====", tradeDate, inserted);
        return inserted;
    }

    /**
     * 同时聚合两个快照表
     */
    public Map<String, Object> aggregateBoth(LocalDate tradeDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tradeDate", tradeDate.toString());

        int plateCount = aggregatePlateDailySnapshot(tradeDate);
        result.put("plateCount", plateCount);

        int breadthCount = aggregateMarketBreadthDailySnapshot(tradeDate);
        result.put("breadthCount", breadthCount);

        return result;
    }

    private String getDataSourceUrl() {
        try {
            return clickHouseDataSource.getConnection().getMetaData().getURL();
        } catch (Exception e) {
            return "UNKNOWN: " + e.getMessage();
        }
    }
}
