CREATE TABLE IF NOT EXISTS wushi.judgement_evidence_item
(
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    engine_type String COMMENT '引擎类型，CYCLE/MAINLINE/LEADER/PATTERN/RISK等',
    target_type String COMMENT '对象类型',
    target_code String COMMENT '对象代码',
    evidence_id String COMMENT '证据 ID',
    evidence_type String COMMENT '证据类型，SUPPORT/CONFLICT/WARNING/VALIDATION',
    factor_code String COMMENT '因子代码',
    factor_name String COMMENT '因子名称',
    evidence_title String COMMENT '证据标题',
    evidence_desc String COMMENT '证据描述',
    factor_value Decimal(18, 6) COMMENT '因子值',
    score Decimal(10, 4) COMMENT '证据得分',
    weight Decimal(10, 4) COMMENT '证据权重',
    source_table String COMMENT '来源表',
    source_key String COMMENT '来源主键或业务键',
    rule_version String COMMENT '规则版本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, judgement_id, evidence_type, evidence_id)
COMMENT '判断证据明细表';

CREATE TABLE IF NOT EXISTS wushi.next_day_watch_item
(
    watch_id String COMMENT '观察点 ID',
    judgement_id String COMMENT '来源判断 ID',
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

CREATE TABLE IF NOT EXISTS wushi.judgement_forward_validation
(
    validation_id String COMMENT '验证 ID',
    judgement_id String COMMENT '被验证判断 ID',
    trade_date Date COMMENT '原判断交易日',
    validation_date Date COMMENT '验证交易日',
    forward_days UInt8 COMMENT '向后验证天数，1/3/5/10',
    engine_type String COMMENT '引擎类型',
    target_type String COMMENT '对象类型',
    target_code String COMMENT '对象代码',
    validation_result String COMMENT '验证结果，HIT/MISS/CONFLICT_HIT/INSUFFICIENT',
    realized_signal String COMMENT '实际走出的市场信号',
    return_pct Decimal(18, 6) COMMENT '验证期收益率，适用于股票/板块',
    max_drawdown_pct Decimal(18, 6) COMMENT '验证期最大回撤',
    score_delta Decimal(10, 4) COMMENT '本次验证对经验分的影响',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, validation_date, judgement_id, forward_days)
COMMENT '判断 T+N 事后验证表';

CREATE TABLE IF NOT EXISTS wushi.evidence_validation_item
(
    validation_id String COMMENT '验证 ID',
    evidence_id String COMMENT '证据 ID',
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '原判断交易日',
    validation_date Date COMMENT '验证交易日',
    factor_code String COMMENT '因子代码',
    evidence_type String COMMENT '原证据类型',
    validation_result String COMMENT '证据验证结果，VALID/INVALID/CONFLICT_VALID/WARNING_VALID/INSUFFICIENT',
    contribution_score Decimal(10, 4) COMMENT '证据贡献分',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, validation_date, judgement_id, evidence_id)
COMMENT '证据级验证明细表';

CREATE TABLE IF NOT EXISTS wushi.historical_similarity_match
(
    match_id String COMMENT '相似匹配 ID',
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

CREATE TABLE IF NOT EXISTS wushi.historical_similarity_factor_detail
(
    match_id String COMMENT '相似匹配 ID',
    factor_code String COMMENT '相似因子代码',
    factor_name String COMMENT '相似因子名称',
    current_value Decimal(18, 6) COMMENT '当前样本因子值',
    historical_value Decimal(18, 6) COMMENT '历史样本因子值',
    similarity_score Decimal(10, 4) COMMENT '该因子相似度',
    weight Decimal(10, 4) COMMENT '该因子权重',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (match_id, factor_code)
COMMENT '历史相似因子明细表';

CREATE TABLE IF NOT EXISTS wushi.historical_similarity_forward_performance
(
    match_id String COMMENT '相似匹配 ID',
    forward_days UInt8 COMMENT '后续天数，1/3/5/10',
    return_pct Decimal(18, 6) COMMENT '后续收益率',
    max_drawdown_pct Decimal(18, 6) COMMENT '后续最大回撤',
    cycle_change String COMMENT '后续周期变化',
    mainline_change String COMMENT '后续主线变化',
    risk_change String COMMENT '后续风险变化',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
ORDER BY (match_id, forward_days)
COMMENT '历史相似样本后验表现表';
