CREATE DATABASE IF NOT EXISTS wushi COMMENT '悟势 ClickHouse 数据库';

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

CREATE TABLE IF NOT EXISTS wushi.stock_daily_kline
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    open Decimal(18, 4) COMMENT '开盘价',
    high Decimal(18, 4) COMMENT '最高价',
    low Decimal(18, 4) COMMENT '最低价',
    close Decimal(18, 4) COMMENT '收盘价',
    pre_close Decimal(18, 4) COMMENT '前收盘价',
    change_amount Decimal(18, 4) COMMENT '涨跌额',
    change_pct Decimal(18, 6) COMMENT '涨跌幅，百分比值',
    volume UInt64 COMMENT '成交量，股',
    amount Decimal(24, 4) COMMENT '成交额，元',
    turnover_rate Decimal(18, 6) COMMENT '换手率，百分比值',
    amplitude Decimal(18, 6) COMMENT '振幅，百分比值',
    limit_up_price Decimal(18, 4) COMMENT '当日涨停价',
    limit_down_price Decimal(18, 4) COMMENT '当日跌停价',
    total_market_value Decimal(24, 4) COMMENT '总市值，元',
    float_market_value Decimal(24, 4) COMMENT '流通市值，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code)
COMMENT '股票日 K 事实表';

CREATE TABLE IF NOT EXISTS wushi.stock_minute_kline
(
    trade_date Date COMMENT '交易日期',
    minute_time DateTime COMMENT '分钟时间',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    open Decimal(18, 4) COMMENT '分钟开盘价',
    high Decimal(18, 4) COMMENT '分钟最高价',
    low Decimal(18, 4) COMMENT '分钟最低价',
    close Decimal(18, 4) COMMENT '分钟收盘价',
    volume UInt64 COMMENT '分钟成交量，股',
    amount Decimal(24, 4) COMMENT '分钟成交额，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMMDD(trade_date)
ORDER BY (trade_date, stock_code, minute_time)
COMMENT '股票分钟 K 事实表';

CREATE TABLE IF NOT EXISTS wushi.index_daily_kline
(
    trade_date Date COMMENT '交易日期',
    index_code String COMMENT '指数代码',
    index_name String COMMENT '指数名称',
    index_type String COMMENT '指数类型，例如 broad/core/style',
    open Decimal(18, 4) COMMENT '开盘点位',
    high Decimal(18, 4) COMMENT '最高点位',
    low Decimal(18, 4) COMMENT '最低点位',
    close Decimal(18, 4) COMMENT '收盘点位',
    pre_close Decimal(18, 4) COMMENT '前收盘点位',
    change_pct Decimal(18, 6) COMMENT '涨跌幅，百分比值',
    volume UInt64 COMMENT '成交量',
    amount Decimal(24, 4) COMMENT '成交额，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, index_code)
COMMENT '指数日 K 事实表';

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

CREATE TABLE IF NOT EXISTS wushi.stock_plate_daily_kline
(
    trade_date Date COMMENT '交易日期',
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型，行业/概念/风格/地域/指数',
    open Decimal(18, 4) COMMENT '开盘值',
    high Decimal(18, 4) COMMENT '最高值',
    low Decimal(18, 4) COMMENT '最低值',
    close Decimal(18, 4) COMMENT '收盘值',
    pre_close Decimal(18, 4) COMMENT '前收盘值',
    change_pct Decimal(18, 6) COMMENT '涨跌幅，百分比值',
    volume UInt64 COMMENT '成交量',
    amount Decimal(24, 4) COMMENT '成交额，元',
    turnover_rate Decimal(18, 6) COMMENT '换手率，百分比值',
    main_net_inflow Decimal(24, 4) COMMENT '主力净流入，元，缺失填 0 并在数据质量表记录',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, plate_type, plate_code)
COMMENT '板块日 K 事实表';

CREATE TABLE IF NOT EXISTS wushi.stock_plate_relation_snapshot
(
    trade_date Date COMMENT '关系快照日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型',
    relation_source String COMMENT '关系来源，HISTORICAL_CRAWLED/CURRENT_BACKFILL/MANUAL_CONFIRMED',
    relation_confidence Decimal(10, 4) COMMENT '关系可信度，0 到 1',
    is_current_backfill UInt8 COMMENT '是否用当前关系回填历史，1 是，0 否',
    source String COMMENT '原始数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code, plate_type, plate_code)
COMMENT '股票与板块关系每日快照';

CREATE TABLE IF NOT EXISTS wushi.stock_limit_status_daily
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    limit_status String COMMENT '涨跌停状态，LIMIT_UP/LIMIT_DOWN/BROKEN_LIMIT/OPEN_LIMIT/NORMAL',
    limit_up_type String COMMENT '涨停类型，FIRST/RELAY/HIGH/ONE_WORD/UNKNOWN',
    consecutive_limit_up_days UInt16 COMMENT '连续涨停天数',
    first_limit_time Nullable(DateTime) COMMENT '首次封涨停时间',
    last_limit_time Nullable(DateTime) COMMENT '最后封涨停时间',
    open_limit_times UInt16 COMMENT '开板次数',
    seal_amount Decimal(24, 4) COMMENT '封单金额，元',
    seal_volume UInt64 COMMENT '封单量，股',
    broken_limit_reason String COMMENT '炸板原因描述，无法识别则为空',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code)
COMMENT '个股涨跌停状态每日表';

CREATE TABLE IF NOT EXISTS wushi.stock_limit_intraday_event
(
    trade_date Date COMMENT '交易日期',
    event_time DateTime COMMENT '事件时间',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    event_type String COMMENT '事件类型，TOUCH_LIMIT/SEAL_LIMIT/OPEN_LIMIT/REFILL_LIMIT/BROKEN_LIMIT',
    price Decimal(18, 4) COMMENT '事件发生价',
    seal_amount Decimal(24, 4) COMMENT '事件时封单金额，元',
    event_sequence UInt16 COMMENT '当日事件序号',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = MergeTree
PARTITION BY toYYYYMMDD(trade_date)
ORDER BY (trade_date, stock_code, event_time, event_sequence)
COMMENT '涨停盘中事件表';

CREATE TABLE IF NOT EXISTS wushi.market_breadth_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    up_count UInt32 COMMENT '上涨家数',
    down_count UInt32 COMMENT '下跌家数',
    flat_count UInt32 COMMENT '平盘家数',
    limit_up_count UInt32 COMMENT '涨停家数',
    limit_down_count UInt32 COMMENT '跌停家数',
    broken_limit_count UInt32 COMMENT '炸板家数',
    high_open_count UInt32 COMMENT '高开家数',
    low_open_count UInt32 COMMENT '低开家数',
    above_ma5_count UInt32 COMMENT '收盘价在 5 日线上方家数',
    above_ma10_count UInt32 COMMENT '收盘价在 10 日线上方家数',
    above_ma20_count UInt32 COMMENT '收盘价在 20 日线上方家数',
    money_effect_score Decimal(10, 4) COMMENT '赚钱效应评分',
    loss_effect_score Decimal(10, 4) COMMENT '亏钱效应评分',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY trade_date
COMMENT '全市场宽度每日快照';

CREATE TABLE IF NOT EXISTS wushi.limit_ladder_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    ladder_level UInt16 COMMENT '连板高度，1 表示首板',
    stock_count UInt32 COMMENT '该高度股票数量',
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

CREATE TABLE IF NOT EXISTS wushi.high_position_feedback_daily
(
    trade_date Date COMMENT '交易日期',
    stock_code String COMMENT '高位股代码',
    stock_name String COMMENT '高位股名称',
    position_level UInt16 COMMENT '高位层级，通常用连板高度或趋势高度表达',
    feedback_type String COMMENT '反馈类型，BIG_DROP/LIMIT_DOWN/BROKEN_LIMIT/HIGH_VOLUME_FAIL/WEAK_REPAIR',
    change_pct Decimal(18, 6) COMMENT '当日涨跌幅',
    drawdown_pct Decimal(18, 6) COMMENT '日内最大回撤幅度',
    impact_score Decimal(10, 4) COMMENT '负反馈影响评分',
    related_plate_codes Array(String) COMMENT '关联板块代码',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, stock_code, feedback_type)
COMMENT '高位股负反馈每日表';

CREATE TABLE IF NOT EXISTS wushi.plate_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型',
    stock_count UInt32 COMMENT '板块成分股数量',
    up_count UInt32 COMMENT '上涨家数',
    down_count UInt32 COMMENT '下跌家数',
    limit_up_count UInt32 COMMENT '涨停家数',
    broken_limit_count UInt32 COMMENT '炸板家数',
    limit_down_count UInt32 COMMENT '跌停家数',
    avg_change_pct Decimal(18, 6) COMMENT '平均涨跌幅',
    median_change_pct Decimal(18, 6) COMMENT '涨跌幅中位数',
    amount Decimal(24, 4) COMMENT '板块成交额，元',
    main_net_inflow Decimal(24, 4) COMMENT '主力净流入，元',
    leader_stock_code String COMMENT '领涨股代码',
    leader_stock_name String COMMENT '领涨股名称',
    ladder_integrity_score Decimal(10, 4) COMMENT '梯队完整度评分',
    sustainability_score Decimal(10, 4) COMMENT '持续性评分',
    relation_quality_level String COMMENT '成分关系质量，HIGH/MEDIUM/LOW',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, plate_type, plate_code)
COMMENT '板块每日统计快照';

CREATE TABLE IF NOT EXISTS wushi.capital_flow_daily_snapshot
(
    trade_date Date COMMENT '交易日期',
    target_type String COMMENT '对象类型，MARKET/INDEX/PLATE/STOCK',
    target_code String COMMENT '对象代码',
    target_name String COMMENT '对象名称',
    main_net_inflow Decimal(24, 4) COMMENT '主力净流入，元',
    super_large_net_inflow Decimal(24, 4) COMMENT '超大单净流入，元',
    large_net_inflow Decimal(24, 4) COMMENT '大单净流入，元',
    medium_net_inflow Decimal(24, 4) COMMENT '中单净流入，元',
    small_net_inflow Decimal(24, 4) COMMENT '小单净流入，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, target_type, target_code)
COMMENT '资金流每日快照';

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
    confidence_penalty Decimal(10, 4) COMMENT '对判断置信度的扣减',
    description String COMMENT '问题描述',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, data_domain, issue_id)
COMMENT '数据质量问题与页面影响表';
