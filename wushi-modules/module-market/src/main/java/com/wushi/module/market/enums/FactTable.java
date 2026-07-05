package com.wushi.module.market.enums;

public enum FactTable {

    TRADING_CALENDAR("wushi.trading_calendar", "trade_date"),
    STOCK_POOL_DAILY_SNAPSHOT("wushi.stock_pool_daily_snapshot", "trade_date"),
    STOCK_DAILY_KLINE("wushi.stock_daily_kline", "trade_date"),
    STOCK_MINUTE_KLINE("wushi.stock_minute_kline", "trade_date"),
    INDEX_DAILY_KLINE("wushi.index_daily_kline", "trade_date"),
    STOCK_PLATE_DIMENSION("wushi.stock_plate_dimension", null),
    STOCK_PLATE_DAILY_KLINE("wushi.stock_plate_daily_kline", "trade_date"),
    STOCK_PLATE_RELATION_SNAPSHOT("wushi.stock_plate_relation_snapshot", "trade_date"),
    STOCK_LIMIT_STATUS_DAILY("wushi.stock_limit_status_daily", "trade_date"),
    STOCK_LIMIT_INTRADAY_EVENT("wushi.stock_limit_intraday_event", "trade_date"),
    MARKET_BREADTH_DAILY_SNAPSHOT("wushi.market_breadth_daily_snapshot", "trade_date"),
    LIMIT_LADDER_DAILY_SNAPSHOT("wushi.limit_ladder_daily_snapshot", "trade_date"),
    HIGH_POSITION_FEEDBACK_DAILY("wushi.high_position_feedback_daily", "trade_date"),
    PLATE_DAILY_SNAPSHOT("wushi.plate_daily_snapshot", "trade_date"),
    CAPITAL_FLOW_DAILY_SNAPSHOT("wushi.capital_flow_daily_snapshot", "trade_date"),
    DATA_QUALITY_ISSUE("wushi.data_quality_issue", "trade_date");

    private final String tableName;
    private final String tradeDateColumn;

    FactTable(String tableName, String tradeDateColumn) {
        this.tableName = tableName;
        this.tradeDateColumn = tradeDateColumn;
    }

    public String tableName() {
        return tableName;
    }

    public String tradeDateColumn() {
        return tradeDateColumn;
    }

    public boolean hasTradeDate() {
        return tradeDateColumn != null;
    }
}
