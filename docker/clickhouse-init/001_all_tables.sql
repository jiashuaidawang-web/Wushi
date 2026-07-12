-- ============================================================================
-- 悟势 ClickHouse DDL v2 — 全量表
-- 规则: 数值类(Decimal/UInt/Int/Float)一律 Nullable; 标识/状态/String 主键列保持非空
-- 建表前需先 DROP 旧表: DROP TABLE IF EXISTS wushi.table_name;
-- ============================================================================

CREATE DATABASE IF NOT EXISTS wushi COMMENT '悟势 ClickHouse 数据库';


-- ---------------------------------------------------------------------------
-- 1. 交易日历
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.trading_calendar
(
    trade_date Date COMMENT '交易日期',
    market String COMMENT '市场代码',
    is_trade_day UInt8 COMMENT '是否交易日，1是0否',
    prev_trade_date Nullable(Date) COMMENT '上一交易日',
    next_trade_date Nullable(Date) COMMENT '下一交易日',
    week_of_year Nullable(UInt16) COMMENT '年内周序号',
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
    pool_type String COMMENT '股票池类型，LIMIT_UP/LIMIT_DOWN/STRONG/SUB_NEW/BROKEN/YEST_LIMIT_UP',
    market String COMMENT '市场，SH/SZ/BJ',
    board String COMMENT '上市板块',
    is_st UInt8 COMMENT '是否ST，1是0否',
    is_new_stock UInt8 COMMENT '是否新股，1是0否',
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
-- 3. 股票日 K 事实表
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
    change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅',
    volume Nullable(UInt64) COMMENT '成交量，股',
    amount Nullable(Decimal(24, 4)) COMMENT '成交额，元',
    turnover_rate Nullable(Decimal(18, 6)) COMMENT '换手率',
    amplitude Nullable(Decimal(18, 6)) COMMENT '振幅',
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
COMMENT '股票日K事实表';


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
COMMENT '股票分钟K事实表';


-- ---------------------------------------------------------------------------
-- 5. 指数日 K 事实表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.index_daily_kline
(
    trade_date Date COMMENT '交易日期',
    index_code String COMMENT '指数代码',
    index_name String COMMENT '指数名称',
    index_type String COMMENT '指数类型',
    open Nullable(Decimal(18, 4)) COMMENT '开盘点位',
    high Nullable(Decimal(18, 4)) COMMENT '最高点位',
    low Nullable(Decimal(18, 4)) COMMENT '最低点位',
    close Nullable(Decimal(18, 4)) COMMENT '收盘点位',
    pre_close Nullable(Decimal(18, 4)) COMMENT '前收盘点位',
    change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅',
    volume Nullable(UInt64) COMMENT '成交量',
    amount Nullable(Decimal(24, 4)) COMMENT '成交额，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, index_code)
COMMENT '指数日K事实表';


-- ---------------------------------------------------------------------------
-- 6. 板块字典表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.stock_plate_dimension
(
    plate_code String COMMENT '板块代码',
    plate_name String COMMENT '板块名称',
    plate_type String COMMENT '板块类型',
    parent_plate_code String COMMENT '父级板块代码',
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
    change_pct Nullable(Decimal(18, 6)) COMMENT '涨跌幅',
    volume Nullable(UInt64) COMMENT '成交量，股',
    amount Nullable(Decimal(24, 4)) COMMENT '成交额，元',
    turnover_rate Nullable(Decimal(18, 6)) COMMENT '换手率',
    main_net_inflow Nullable(Decimal(24, 4)) COMMENT '主力净流入，元',
    source String COMMENT '数据来源',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, plate_type, plate_code)
COMMENT '板块日K事实表';


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
    is_leader UInt8 COMMENT '是否为龙头，1是0否',
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
    limit_status String COMMENT '涨跌电状态，LIMIT_UP/LIMIT_DOWN/NORMAL',
    limit_count Nullable(UInt16) COMMENT '连板数',
    highest_board Nullable(UInt16) COMMENT '历史最高连板',
    failed_limit UInt8 COMMENT '是否炸板，1是0否',
    sealed UInt8 COMMENT '是否封死，1是0否',
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
    event_type String COMMENT '事件类型',
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
    jdp_ratio Nullable(Decimal(10, 4)) COMMENT '净多头比',
    sentiment_score Nullable(Decimal(10, 4)) COMMENT '市场情绪评分0-100',
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
    ladder_level UInt16 COMMENT '连板高度',
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
    position_level UInt16 COMMENT '高位层级',
    feedback_type String COMMENT '反馈类型',
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
    relation_quality_level String COMMENT '成分关系质量',
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
    issue_id String COMMENT '问题ID',
    data_domain String COMMENT '数据域',
    table_name String COMMENT '受影响表名',
    issue_type String COMMENT '问题类型',
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


-- ---------------------------------------------------------------------------
-- 17. 周期判断快照表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.cycle_judgement_snapshot
(
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    market_cycle_stage String COMMENT '市场周期阶段',
    emotion_cycle_stage String COMMENT '情绪周期阶段',
    money_effect_score Nullable(Decimal(10, 4)) COMMENT '赚钱效应评分',
    loss_effect_score Nullable(Decimal(10, 4)) COMMENT '亏钱效应评分',
    stage_score Nullable(Decimal(10, 4)) COMMENT '周期阶段综合评分',
    confidence Nullable(Decimal(10, 4)) COMMENT '判断置信度，0到1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    evidence_count Nullable(UInt16) COMMENT '支持证据数量',
    conflict_count Nullable(UInt16) COMMENT '冲突证据数量',
    warning_count Nullable(UInt16) COMMENT '风险提示数量',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, judgement_id)
COMMENT '周期判断快照表';


-- ---------------------------------------------------------------------------
-- 18. 主线识别判断快照表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.mainline_judgement_snapshot
(
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式',
    plate_code String COMMENT '主线候选板块代码',
    plate_name String COMMENT '主线候选板块名称',
    plate_type String COMMENT '板块类型',
    mainline_status String COMMENT '主线状态',
    strength_score Nullable(Decimal(10, 4)) COMMENT '主线强度评分',
    continuity_score Nullable(Decimal(10, 4)) COMMENT '连续活跃评分',
    ladder_integrity_score Nullable(Decimal(10, 4)) COMMENT '涨停梯队完整度评分',
    leader_quality_score Nullable(Decimal(10, 4)) COMMENT '龙头质量评分',
    middle_army_support_score Nullable(Decimal(10, 4)) COMMENT '中军承接评分',
    rear_risk_score Nullable(Decimal(10, 4)) COMMENT '后排风险评分',
    confidence Nullable(Decimal(10, 4)) COMMENT '判断置信度，0到1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, plate_code, judgement_id)
COMMENT '主线识别判断快照表';


-- ---------------------------------------------------------------------------
-- 19. 龙头竞争判断快照表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.leader_judgement_snapshot
(
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    plate_code String COMMENT '所属主线或分支板块代码',
    plate_name String COMMENT '所属主线或分支板块名称',
    leader_type String COMMENT '龙头类型',
    leader_status String COMMENT '龙头状态',
    position_score Nullable(Decimal(10, 4)) COMMENT '地位评分',
    popularity_score Nullable(Decimal(10, 4)) COMMENT '人气评分',
    drive_score Nullable(Decimal(10, 4)) COMMENT '带动性评分',
    divergence_repair_score Nullable(Decimal(10, 4)) COMMENT '分歧修复评分',
    challenge_risk_score Nullable(Decimal(10, 4)) COMMENT '被挑战风险评分',
    confidence Nullable(Decimal(10, 4)) COMMENT '判断置信度，0到1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, stock_code, judgement_id)
COMMENT '龙头竞争判断快照表';


-- ---------------------------------------------------------------------------
-- 20. 分歧一致判断快照表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.divergence_consensus_snapshot
(
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式',
    target_type String COMMENT '对象类型，MARKET/PLATE/STOCK',
    target_code String COMMENT '对象代码',
    target_name String COMMENT '对象名称',
    state String COMMENT '分歧一致状态',
    divergence_score Nullable(Decimal(10, 4)) COMMENT '分歧评分',
    consensus_score Nullable(Decimal(10, 4)) COMMENT '一致评分',
    refill_quality_score Nullable(Decimal(10, 4)) COMMENT '回封质量评分',
    broken_limit_risk_score Nullable(Decimal(10, 4)) COMMENT '炸板风险评分',
    rear_feedback_score Nullable(Decimal(10, 4)) COMMENT '后排反馈评分',
    confidence Nullable(Decimal(10, 4)) COMMENT '判断置信度，0到1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, target_type, target_code, judgement_id)
COMMENT '分歧一致判断快照表';


-- ---------------------------------------------------------------------------
-- 21. 风险雷达判断快照表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.risk_radar_snapshot
(
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式',
    target_type String COMMENT '对象类型，MARKET/PLATE/STOCK',
    target_code String COMMENT '对象代码',
    target_name String COMMENT '对象名称',
    risk_level String COMMENT '风险等级，LOW/MEDIUM/HIGH/EXTREME',
    risk_type String COMMENT '风险类型',
    risk_score Nullable(Decimal(10, 4)) COMMENT '风险评分',
    high_position_feedback_score Nullable(Decimal(10, 4)) COMMENT '高位负反馈评分',
    broken_limit_score Nullable(Decimal(10, 4)) COMMENT '炸板风险评分',
    loss_spread_score Nullable(Decimal(10, 4)) COMMENT '亏钱效应扩散评分',
    confidence Nullable(Decimal(10, 4)) COMMENT '判断置信度，0到1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, target_type, target_code, judgement_id)
COMMENT '风险雷达判断快照表';


-- ---------------------------------------------------------------------------
-- 22. 市场总览聚合快照表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.market_overview_snapshot
(
    judgement_id String COMMENT '总览判断ID',
    trade_date Date COMMENT '被展示交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式',
    market_cycle_stage String COMMENT '市场周期阶段',
    emotion_cycle_stage String COMMENT '情绪周期阶段',
    primary_mainline_code String COMMENT '第一主线代码',
    primary_mainline_name String COMMENT '第一主线名称',
    market_leader_code String COMMENT '市场总龙代码',
    market_leader_name String COMMENT '市场总龙名称',
    divergence_state String COMMENT '市场分歧一致状态',
    risk_level String COMMENT '市场风险等级',
    confidence Nullable(Decimal(10, 4)) COMMENT '总览置信度',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    summary String COMMENT '市场总览摘要',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, judgement_id)
COMMENT '市场总览聚合快照表';


-- ---------------------------------------------------------------------------
-- 23. 判断证据明细表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.judgement_evidence_item
(
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式',
    engine_type String COMMENT '引擎类型',
    target_type String COMMENT '对象类型',
    target_code String COMMENT '对象代码',
    evidence_id String COMMENT '证据ID',
    evidence_type String COMMENT '证据类型，SUPPORT/CONFLICT/WARNING/VALIDATION',
    factor_code String COMMENT '因子代码',
    factor_name String COMMENT '因子名称',
    evidence_title String COMMENT '证据标题',
    evidence_desc String COMMENT '证据描述',
    factor_value Nullable(Decimal(18, 6)) COMMENT '因子值',
    score Nullable(Decimal(10, 4)) COMMENT '证据得分',
    weight Nullable(Decimal(10, 4)) COMMENT '证据权重',
    source_table String COMMENT '来源表',
    source_key String COMMENT '来源主键或业务键',
    rule_version String COMMENT '规则版本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, judgement_id, evidence_type, evidence_id)
COMMENT '判断证据明细表';


-- ---------------------------------------------------------------------------
-- 24. 明日及未来观察验证点表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.next_day_watch_item
(
    watch_id String COMMENT '观察点ID',
    judgement_id String COMMENT '来源判断ID',
    trade_date Date COMMENT '生成观察点的交易日',
    watch_date Date COMMENT '需要验证的交易日',
    as_of_date Date COMMENT '生成观察点时的观察日期',
    judgement_mode String COMMENT '判断模式',
    engine_type String COMMENT '来源引擎类型',
    target_type String COMMENT '观察对象类型',
    target_code String COMMENT '观察对象代码',
    target_name String COMMENT '观察对象名称',
    watch_title String COMMENT '观察点标题',
    condition_expression String COMMENT '验证条件表达式',
    expected_signal String COMMENT '符合预期时的信号',
    risk_signal String COMMENT '不符合预期时的风险信号',
    priority UInt8 COMMENT '优先级，数值越大越重要',
    rule_version String COMMENT '规则版本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, watch_date, engine_type, target_type, target_code, watch_id)
COMMENT '明日及未来观察验证点表';


-- ---------------------------------------------------------------------------
-- 25. 判断 T+N 事后验证表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.judgement_forward_validation
(
    validation_id String COMMENT '验证ID',
    judgement_id String COMMENT '被验证判断ID',
    trade_date Date COMMENT '原判断交易日',
    validation_date Date COMMENT '验证交易日',
    forward_days UInt8 COMMENT '向后验证天数，1/3/5/10',
    engine_type String COMMENT '引擎类型',
    target_type String COMMENT '对象类型',
    target_code String COMMENT '对象代码',
    validation_result String COMMENT '验证结果，HIT/MISS/CONFLICT_HIT/INSUFFICIENT',
    realized_signal String COMMENT '实际走出的市场信号',
    return_pct Nullable(Decimal(18, 6)) COMMENT '验证期收益率',
    max_drawdown_pct Nullable(Decimal(18, 6)) COMMENT '验证期最大回撤',
    score_delta Nullable(Decimal(10, 4)) COMMENT '本次验证对经验分的影响',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, validation_date, judgement_id, forward_days)
COMMENT '判断T+N事后验证表';


-- ---------------------------------------------------------------------------
-- 26. 证据级验证明细表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.evidence_validation_item
(
    validation_id String COMMENT '验证ID',
    evidence_id String COMMENT '证据ID',
    judgement_id String COMMENT '判断ID',
    trade_date Date COMMENT '原判断交易日',
    validation_date Date COMMENT '验证交易日',
    factor_code String COMMENT '因子代码',
    evidence_type String COMMENT '原证据类型',
    validation_result String COMMENT '证据验证结果',
    contribution_score Nullable(Decimal(10, 4)) COMMENT '证据贡献分',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, validation_date, judgement_id, evidence_id)
COMMENT '证据级验证明细表';


-- ---------------------------------------------------------------------------
-- 27. 历史相似匹配结果表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.historical_similarity_match
(
    match_id String COMMENT '相似匹配ID',
    trade_date Date COMMENT '当前交易日',
    as_of_date Date COMMENT '站在哪一天观察当前交易日',
    judgement_mode String COMMENT '判断模式',
    engine_type String COMMENT '相似匹配来源引擎',
    target_type String COMMENT '当前对象类型',
    target_code String COMMENT '当前对象代码',
    similar_trade_date Date COMMENT '历史相似交易日',
    similar_target_code String COMMENT '历史相似对象代码',
    similarity_score Decimal(10, 4) COMMENT '相似度评分',
    forward_summary String COMMENT '历史样本后续表现摘要',
    rule_version String COMMENT '规则版本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, target_type, target_code, similarity_score, match_id)
COMMENT '历史相似匹配结果表';


-- ---------------------------------------------------------------------------
-- 28. 历史相似因子明细表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.historical_similarity_factor_detail
(
    match_id String COMMENT '相似匹配ID',
    factor_code String COMMENT '相似因子代码',
    factor_name String COMMENT '相似因子名称',
    current_value Nullable(Decimal(18, 6)) COMMENT '当前样本因子值',
    historical_value Nullable(Decimal(18, 6)) COMMENT '历史样本因子值',
    similarity_score Nullable(Decimal(10, 4)) COMMENT '该因子相似度',
    weight Nullable(Decimal(10, 4)) COMMENT '该因子权重',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (match_id, factor_code)
COMMENT '历史相似因子明细表';


-- ---------------------------------------------------------------------------
-- 29. 历史相似样本后验表现表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wushi.historical_similarity_forward_performance
(
    match_id String COMMENT '相似匹配ID',
    forward_days UInt8 COMMENT '后续天数，1/3/5/10',
    return_pct Nullable(Decimal(18, 6)) COMMENT '后续收益率',
    max_drawdown_pct Nullable(Decimal(18, 6)) COMMENT '后续最大回撤',
    cycle_change String COMMENT '后续周期变化',
    mainline_change String COMMENT '后续主线变化',
    risk_change String COMMENT '后续风险变化',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (match_id, forward_days)
COMMENT '历史相似样本后验表现表';
