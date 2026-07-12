package com.wushi.module.spider.audit.impl;

import com.wushi.module.market.enums.FactTable;
import com.wushi.module.spider.audit.SpiderValidationService;
import com.wushi.module.spider.audit.mapper.DataSyncAuditLogMapper;
import com.wushi.module.spider.audit.entity.DataSyncAuditLogEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderValidationServiceImpl implements SpiderValidationService {

    @Qualifier("clickHouseJdbcTemplate")
    private final JdbcTemplate clickHouseJdbcTemplate;

    private final DataSyncAuditLogMapper auditLogMapper;

    /**
     * 需要校验的核心事实表 (按 FactTable 枚举中 hasTradeDate 的表)
     */
    private static final List<FactTable> TABLES_TO_VALIDATE = List.of(
            FactTable.STOCK_DAILY_KLINE,
            FactTable.INDEX_DAILY_KLINE,
            FactTable.STOCK_PLATE_DIMENSION,
            FactTable.STOCK_PLATE_DAILY_KLINE,
            FactTable.STOCK_PLATE_RELATION_SNAPSHOT,
            FactTable.STOCK_LIMIT_STATUS_DAILY,
            FactTable.STOCK_LIMIT_INTRADAY_EVENT,
            FactTable.STOCK_POOL_DAILY_SNAPSHOT,
            FactTable.CAPITAL_FLOW_DAILY_SNAPSHOT,
            FactTable.MARKET_BREADTH_DAILY_SNAPSHOT,
            FactTable.PLATE_DAILY_SNAPSHOT
    );

    @Override
    public List<String> validate(LocalDate tradeDate) {
        log.info("开始数据校验: tradeDate={}", tradeDate);
        List<String> failedTables = new ArrayList<>();

        for (FactTable table : TABLES_TO_VALIDATE) {
            if (!table.hasTradeDate()) {
                continue;
            }
            String tableName = table.tableName();
            int count = queryCount(tableName, table.tradeDateColumn(), tradeDate);
            if (count <= 0) {
                failedTables.add(tableName);
                auditFailure(tradeDate, tableName, count);
                log.warn("数据校验未通过: table={}, tradeDate={}, count={}", tableName, tradeDate, count);
            } else {
                log.info("数据校验通过: table={}, count={}", tableName, count);
            }
        }

        log.info("数据校验完成: tradeDate={}, failedCount={}", tradeDate, failedTables.size());
        return failedTables;
    }

    private int queryCount(String tableName, String tradeDateColumn, LocalDate tradeDate) {
        try {
            String sql = String.format("SELECT count() AS cnt FROM %s WHERE %s = ?", tableName, tradeDateColumn);
            Integer count = clickHouseJdbcTemplate.queryForObject(sql, Integer.class, tradeDate.toString());
            return count == null ? 0 : count;
        } catch (Exception e) {
            log.error("查询表数据量失败: table={}, tradeDate={}, error={}", tableName, tradeDate, e.getMessage());
            return 0;
        }
    }

    private void auditFailure(LocalDate tradeDate, String tableName, int count) {
        try {
            DataSyncAuditLogEntity entity = new DataSyncAuditLogEntity();
            entity.setAuditId("VALIDATION-" + UUID.randomUUID().toString().substring(0, 8));
            entity.setTaskCode("spider_validation");
            entity.setTradeDate(tradeDate);
            entity.setProvider("INTERNAL");
            entity.setTargetTable(tableName);
            entity.setSyncStatus("FAILED");
            entity.setFetchedCount(0);
            entity.setInsertedCount(count);
            entity.setUpdatedCount(0);
            entity.setFailedCount(0);
            entity.setErrorMessage("数据校验未通过: count=" + count);
            auditLogMapper.insert(entity);
        } catch (Exception e) {
            log.error("写入审计日志失败: table={}, error={}", tableName, e.getMessage());
        }
    }
}
