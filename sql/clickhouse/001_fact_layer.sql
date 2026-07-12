-- ============================================================================
-- 悟势 ClickHouse 事实层 DDL v2
-- 修改: 所有数值类字段改为 Nullable, 适应东财接口对部分股票不返回部分字段的情况
-- ============================================================================

CREATE DATABASE IF NOT EXISTS wushi COMMENT '悟势 ClickHouse 数据库';


-- ---------------------------------------------------------------------------
-- 1. 交易日历
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.trading_calendar
(
    trade_date Date COMMENT '交易日期',
    market String COMMENT '市场代码，例如 A_SHARE',
    is_trade_day UInt8 COMMENT '是否交易日，1 是，0 否',
    prev_trade_date Nullable(Date) COMMENT '上一交易日',
    next_trade_date Nullable(Date) COMMENT '下一交易日',
    week_of_year UInt16 COMMENT '年内周序号',
    month String COMMENT '月份，格式 yyyy-MM',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = MergeTree
ORDER BY (market, trade_date)
COMMENT '交易日历表';


-- ---------------------------------------------------------------------------
-- 2. 股票池每日快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_pool_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    market String COMMENT '市场，例如 SH/SZ/BJ',
    board String COMMENT '上市板块，例如 主板/创业板/科创板/北交所',
    is_st UInt8 COMMENT '是否 ST，1 是，0 否',
    is_new_stock UInt8 COMMENT '是否新股，1 是，0 否',
    list_date Nullable(Date) COMMENT '上市日期',
    suspend_status String COMMENT '停复牌状态',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code)
COMMENT '股票池每日快照';


-- ---------------------------------------------------------------------------
-- 3. 股票日 K 事实表 (核心表)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_daily_kline
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    open Nullable(Decimal(18, 4)) COMMENT '开盘价',
    high Nullable(Decimal(18, 4)) COMMENT '最高价',
    low Nullable(Decimal(18, 4)) COMMENT '最低价',
    close Nullable(Decimal(18, 4)) COMMENT '收盘价',
    pre_close Nullable(Decimal(18, 4)) COMMENT '前收盘价',
    change_amount Nullable(Decimal(18, 4)) COMMENT '涨跌额',
    change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅，百分比值',
    volume Nullable(UInt64) COMMENT '成交量，股',
    amount Nullable(Decimal(24, 4)) COMMENT '成交额，元',
    turnover_rate Nullable(Decimal(18, 6)) COMMENT '换手率，百分比值',
    amplitude Nullable(Decimal(18, 6)) COMMENT '振幅，百分比值',
    limit_up_price Nullable(Decimal(18, 4)) COMMENT '当日涨停价',
    limit_down_price Nullable(Decimal(18, 4)) COMMENT '当日跌停价',
    total_market_value Nullable(Decimal(24, 4)) COMMENT '总市值，元',
    float_market_value Nullable(Decimal(24, 4)) COMMENT '流通市值，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code)
COMMENT '股票日 K 事实表';


-- ---------------------------------------------------------------------------
-- 4. 股票分钟 K 事实表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_minute_kline
(
    trade_date Date COMMENT '交易日期',
    minute_time DateTime COMMENT '分钟时间',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    open Nullable(Decimal(18, 4)) COMMENT '分钟开盘价',
    high Nullable(Decimal(18, 4)) COMMENT '分钟最高价',
    low Nullable(Decimal(18, 4)) COMMENT '分钟最低价',
    close Nullable(Decimal(18, 4)) COMMENT '分钟收盘价',
    volume Nullable(UInt64) COMMENT '分钟成交量，股',
    amount Nullable(Decimal(24, 4)) COMMENT '分钟成交额，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMMDD(trade_date)
ORDER BY (trade_date, stock_code, minute_time)
COMMENT '股票分钟 K 事实表';


-- ---------------------------------------------------------------------------
-- 5. 指数日 K 事实表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.index_daily_kline
(
    trade_date Date COMMENT '交易日期',
    index_code String COMMENT '指数代码',
    index_name String COMMENT '指数名称',
    index_type String COMMENT '指数类型，例如 broad/core/style',
    open Nullable(Decimal(18, 4)) COMMENT '开盘点位',
    high Nullable(Decimal(18, 4)) COMMENT '最高点位',
    low Nullable(Decimal(18, 4)) COMMENT '最低点位',
    close Nullable(Decimal(18, 4)) COMMENT '收盘点位',
    pre_close Nullable(Decimal(18, 4)) COMMENT '前收盘点位',
    change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅，百分比值',
    volume Nullable(UInt64) COMMENT '成交量',
    amount Nullable(Decimal(24, 4)) COMMENT '成交额，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, index_code)
COMMENT '指数日 K 事实表';


-- ---------------------------------------------------------------------------
-- 6. 板块字典表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_plate_dimension
(
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型，行业/概念/风格/地域/指数',
    parent_plate_code String COMMENT '父级板块代码，没有则为空',
    status String COMMENT '状态，ACTIVE/INACTIVE',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间',
    updated_at DateTime DEFAULT now() COMMENT '更新时间'
)
ENGINE = ReplacingMergeTree(updated_at)
ORDER BY (plate_type, plate_code)
COMMENT '板块字典表';


-- ---------------------------------------------------------------------------
-- 7. 板块日 K 事实表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_plate_daily_kline
(
    trade_date Date COMMENT '交易日期',
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型',
    open Nullable(Decimal(18, 4)) COMMENT '开盘价',
    high Nullable(Decimal(18, 4)) COMMENT '最高价',
    low Nullable(Decimal(18, 4)) COMMENT '最低价',
    close Nullable(Decimal(18, 4)) COMMENT '收盘价',
    pre_close Nullable(Decimal(18, 4)) COMMENT '前收盘价',
    change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅，百分比值',
    volume Nullable(UInt64) COMMENT '成交量，股',
    amount Nullable(Decimal(24, 4)) COMMENT '成交额，元',
    turnover_rate Nullable(Decimal(18, 6)) COMMENT '换手率，百分比值',
    main_net_inflow Nullable(Decimal(24, 4)) COMMENT '主力净流入，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, plate_type, plate_code)
COMMENT '板块日 K 事实表';


-- ---------------------------------------------------------------------------
-- 8. 板块个股关系快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_plate_relation_snapshot
(
    trade_date Date COMMENT '交易日期',
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    weight Nullable(Decimal(10, 6)) COMMENT '个股在板块中的权重',
    is_leader UInt8 COMMENT '是否为龙头，1 是，0 否',
    leader_rank Nullable(UInt16) COMMENT '龙头排序',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, plate_type, plate_code, stock_code)
COMMENT '板块个股关系快照';


-- ---------------------------------------------------------------------------
-- 9. 涨跌停状态每日表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_limit_status_daily
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    limit_status String COMMENT '涨跌停状态，LIMIT_UP/LIMIT_DOWN/NORMAL',
    limit_count Nullable(UInt16) COMMENT '连板数',
    highest_board Nullable(UInt16) COMMENT '历史最高连板',
    failed_limit UInt8 COMMENT '是否炸板，1 是，0 否',
    sealed UInt8 COMMENT '是否封死，1 是，0 否',
    sealed_amount Nullable(Decimal(24, 4)) COMMENT '封单金额，元',
    sealed_times Nullable(UInt16) COMMENT '开板次数',
    first_seal_time Nullable(DateTime) COMMENT '首次封板时间',
    last_seal_time Nullable(DateTime) COMMENT '最后封板时间',
    auction_ratio Nullable(Decimal(10, 6)) COMMENT '竞价量比',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code)
COMMENT '涨跌停状态每日表';


-- ---------------------------------------------------------------------------
-- 10. 涨停盘中事件表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_limit_intraday_event
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    event_type String COMMENT '事件类型，FIRST_SEAL/BROKEN/RESEALED/LIMIT_DOWN_OPEN',
    event_time DateTime COMMENT '事件时刻',
    event_price Nullable(Decimal(18, 4)) COMMENT '事件发生时价格',
    event_amount Nullable(Decimal(24, 4)) COMMENT '事件发生时成交额',
    interval_seconds Nullable(UInt32) COMMENT '距首次封板间隔秒数',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code, event_time)
COMMENT '涨停盘中事件表';


-- ---------------------------------------------------------------------------
-- 11. 全市场宽度每日快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.market_breadth_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    total_stocks Nullable(UInt32) COMMENT '全市场股票总数',
    up_count Nullable(UInt32) COMMENT '上涨家数',
    down_count Nullable(UInt32) COMMENT '下跌家数',
    flat_count Nullable(UInt32) COMMENT '平盘家数',
    limit_up_count Nullable(UInt32) COMMENT '涨停家数',
    limit_down_count Nullable(UInt32) COMMENT '跌停家数',
    broken_limit_count Nullable(UInt32) COMMENT '炸板家数',
    up_down_ratio Nullable(Decimal(10, 4)) COMMENT '涨跌比',
    limit_up_ratio Nullable(Decimal(10, 6)) COMMENT '涨停占比',
    limit_down_ratio Nullable(Decimal(10, 6)) COMMENT '跌停占比',
    yest_limit_up_count Nullable(UInt32) COMMENT '昨日涨停家数',
    yest_limit_up_now_up Nullable(UInt32) COMMENT '昨日涨停今日上涨家数',
    yest_limit_up_now_down Nullable(UInt32) COMMENT '昨日涨停今日下跌家数',
    yest_limit_up_now_limit_up Nullable(UInt32) COMMENT '昨日涨停今日继续涨停家数',
    yest_limit_up_feedback Nullable(Decimal(18, 6)) COMMENT '昨日涨停今日平均涨跌',
    jdp_ratio Nullable(Decimal(10, 4)) COMMENT '净多头比，(up-down)/total',
    sentiment_score Nullable(Decimal(10, 4)) COMMENT '市场情绪评分 0-100',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY trade_date
COMMENT '全市场宽度每日快照';


-- ---------------------------------------------------------------------------
-- 12. 涨停梯队每日快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.limit_ladder_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    ladder_level UInt16 COMMENT '连板高度，1 表示首板',
    stock_count Nullable(UInt32) COMMENT '该高度股票数量',
    stock_codes Array(String) COMMENT '该高度股票代码列表',
    stock_names Array(String) COMMENT '该高度股票名称列表',
    strongest_stock_code String COMMENT '该高度最强代表股票代码',
    strongest_stock_name String COMMENT '该高度最强代表股票名称',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, ladder_level)
COMMENT '涨停梯队每日快照';


-- ---------------------------------------------------------------------------
-- 13. 高位股负反馈每日表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.high_position_feedback_daily
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '高位股代码',
    stock_name String COMMENT '高位股名称',
    position_level UInt16 COMMENT '高位层级，通常用连板高度或趋势高度表达',
    feedback_type String COMMENT '反馈类型，BIG_DROP/LIMIT_DOWN/BROKEN_LIMIT/HIGH_VOLUME_FAIL/WEAK_REPAIR',
    change_pct Nullable(Decimal(18, 6)) COMMENT '当日涨跌幅',
    drawdown_pct Nullable(Decimal(18, 6)) COMMENT '日内最大回撤幅度',
    impact_score Nullable(Decimal(10, 4)) COMMENT '负反馈影响评分',
    related_plate_codes Array(String) COMMENT '关联板块代码',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code, feedback_type)
COMMENT '高位股负反馈每日表';


-- ---------------------------------------------------------------------------
-- 14. 板块每日统计快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.plate_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型',
    stock_count Nullable(UInt32) COMMENT '板块成分股数量',
    up_count Nullable(UInt32) COMMENT '上涨家数',
    down_count Nullable(UInt32) COMMENT '下跌家数',
    limit_up_count Nullable(UInt32) COMMENT '涨停家数',
    broken_limit_count Nullable(UInt32) COMMENT '炸板家数',
    limit_down_count Nullable(UInt32) COMMENT '跌停家数',
    avg_change_pct Nullable(Decimal(18, 6)) COMMENT '平均涨跌幅',
    median_change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅中位数',
    amount Nullable(Decimal(24, 4)) COMMENT '板块成交额，元',
    main_net_inflow Nullable(Decimal(24, 4)) COMMENT '主力净流入，元',
    leader_stock_code String COMMENT '领涨股代码',
    leader_stock_name String COMMENT '领涨股名称',
    ladder_integrity_score Nullable(Decimal(10, 4)) COMMENT '梯队完整度评分',
    sustainability_score Nullable(Decimal(10, 4)) COMMENT '持续性评分',
    relation_quality_level String COMMENT '成分关系质量，HIGH/MEDIUM/LOW',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, plate_type, plate_code)
COMMENT '板块每日统计快照';


-- ---------------------------------------------------------------------------
-- 15. 资金流每日快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.capital_flow_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    target_type String COMMENT '对象类型，MARKET/INDEX/PLATE/STOCK',
    target_code String COMMENT '对象代码',
    target_name String COMMENT '对象名称',
    main_net_inflow Nullable(Decimal(24, 4)) COMMENT '主力净流入，元',
    super_large_net_inflow Nullable(Decimal(24, 4)) COMMENT '超大单净流入，元',
    large_net_inflow Nullable(Decimal(24, 4)) COMMENT '大单净流入，元',
    medium_net_inflow Nullable(Decimal(24, 4)) COMMENT '中单净流入，元',
    small_net_inflow Nullable(Decimal(24, 4)) COMMENT '小单净流入，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, target_type, target_code)
COMMENT '资金流每日快照';


-- ---------------------------------------------------------------------------
-- 16. 数据质量问题与页面影响表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.data_quality_issue
(
    trade_date Date COMMENT '交易日期',
    issue_id String COMMENT '问题 ID',
    data_domain String COMMENT '数据域，例如 MARKET/SPIDER/PLATE/STOCK/LIMIT/CAPITAL',
    table_name String COMMENT '受影响表名',
    issue_type String COMMENT '问题类型，MISSING/DELAYED/PARTIAL/BACKFILLED/CONFLICT',
    severity String COMMENT '严重程度，INFO/WARN/ERROR/BLOCKER',
    impact_level String COMMENT '影响级别，NONE/LOW/MEDIUM/HIGH',
    impact_pages Array(String) COMMENT '受影响页面',
    confidence_penalty Nullable(Decimal(10, 4)) COMMENT '对判断置信度的扣减',
    description String COMMENT '问题描述',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, data_domain, issue_id)
COMMENT '数据质量问题与页面影响表';
