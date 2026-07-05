CREATE TABLE IF NOT EXISTS wushi.cycle_judgement_snapshot
(
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    market_cycle_stage String COMMENT '市场周期阶段，熊头/熊中/熊末/修复/牛初/牛中/牛末',
    emotion_cycle_stage String COMMENT '情绪周期阶段，冰点/弱修复/发酵/高潮/分歧/退潮等',
    money_effect_score Decimal(10, 4) COMMENT '赚钱效应评分',
    loss_effect_score Decimal(10, 4) COMMENT '亏钱效应评分',
    stage_score Decimal(10, 4) COMMENT '周期阶段综合评分',
    confidence Decimal(10, 4) COMMENT '判断置信度，0 到 1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级，HIGH/MEDIUM/LOW/INSUFFICIENT',
    conclusion String COMMENT '判断结论文本',
    evidence_count UInt16 COMMENT '支持证据数量',
    conflict_count UInt16 COMMENT '冲突证据数量',
    warning_count UInt16 COMMENT '风险提示数量',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, judgement_id)
COMMENT '周期判断快照表';

CREATE TABLE IF NOT EXISTS wushi.mainline_judgement_snapshot
(
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    plate_code String COMMENT '主线候选板块代码',
    plate_name String COMMENT '主线候选板块名称',
    plate_type String COMMENT '板块类型',
    mainline_status String COMMENT '主线状态，MAIN/SECONDARY/NEW_THEME/NON_MAIN/CANDIDATE',
    strength_score Decimal(10, 4) COMMENT '主线强度评分',
    continuity_score Decimal(10, 4) COMMENT '连续活跃评分',
    ladder_integrity_score Decimal(10, 4) COMMENT '涨停梯队完整度评分',
    leader_quality_score Decimal(10, 4) COMMENT '龙头质量评分',
    middle_army_support_score Decimal(10, 4) COMMENT '中军承接评分',
    rear_risk_score Decimal(10, 4) COMMENT '后排风险评分',
    confidence Decimal(10, 4) COMMENT '判断置信度，0 到 1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, plate_code, judgement_id)
COMMENT '主线识别判断快照表';

CREATE TABLE IF NOT EXISTS wushi.leader_judgement_snapshot
(
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    stock_code String COMMENT '股票代码',
    stock_name String COMMENT '股票名称',
    plate_code String COMMENT '所属主线或分支板块代码',
    plate_name String COMMENT '所属主线或分支板块名称',
    leader_type String COMMENT '龙头类型，MARKET/MAINLINE/BRANCH/MIDDLE_ARMY/FOLLOWER/MONSTER/TREND',
    leader_status String COMMENT '龙头状态，CANDIDATE/RISING/STABLE/CHALLENGED/DROPPED/DEAD',
    position_score Decimal(10, 4) COMMENT '地位评分',
    popularity_score Decimal(10, 4) COMMENT '人气评分',
    drive_score Decimal(10, 4) COMMENT '带动性评分',
    divergence_repair_score Decimal(10, 4) COMMENT '分歧修复评分',
    challenge_risk_score Decimal(10, 4) COMMENT '被挑战风险评分',
    confidence Decimal(10, 4) COMMENT '判断置信度，0 到 1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, stock_code, judgement_id)
COMMENT '龙头竞争判断快照表';

CREATE TABLE IF NOT EXISTS wushi.divergence_consensus_snapshot
(
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    target_type String COMMENT '对象类型，MARKET/PLATE/STOCK',
    target_code String COMMENT '对象代码',
    target_name String COMMENT '对象名称',
    state String COMMENT '分歧一致状态，WEAK_DIVERGENCE/STRONG_DIVERGENCE/DIVERGENCE_TO_CONSENSUS/ACCELERATED_CONSENSUS/OVERHEATED/RECESSION_DIVERGENCE',
    divergence_score Decimal(10, 4) COMMENT '分歧评分',
    consensus_score Decimal(10, 4) COMMENT '一致评分',
    refill_quality_score Decimal(10, 4) COMMENT '回封质量评分',
    broken_limit_risk_score Decimal(10, 4) COMMENT '炸板风险评分',
    rear_feedback_score Decimal(10, 4) COMMENT '后排反馈评分',
    confidence Decimal(10, 4) COMMENT '判断置信度，0 到 1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, target_type, target_code, judgement_id)
COMMENT '分歧一致判断快照表';

CREATE TABLE IF NOT EXISTS wushi.risk_radar_snapshot
(
    judgement_id String COMMENT '判断 ID',
    trade_date Date COMMENT '被判断交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    target_type String COMMENT '对象类型，MARKET/PLATE/STOCK',
    target_code String COMMENT '对象代码',
    target_name String COMMENT '对象名称',
    risk_level String COMMENT '风险等级，LOW/MEDIUM/HIGH/EXTREME',
    risk_type String COMMENT '风险类型，HIGH_POSITION_FEEDBACK/REAR_BROKEN/LEADER_FAIL/PLATE_LOSS/RECESSION',
    risk_score Decimal(10, 4) COMMENT '风险评分',
    high_position_feedback_score Decimal(10, 4) COMMENT '高位负反馈评分',
    broken_limit_score Decimal(10, 4) COMMENT '炸板风险评分',
    loss_spread_score Decimal(10, 4) COMMENT '亏钱效应扩散评分',
    confidence Decimal(10, 4) COMMENT '判断置信度，0 到 1',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    conclusion String COMMENT '判断结论文本',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, target_type, target_code, judgement_id)
COMMENT '风险雷达判断快照表';

CREATE TABLE IF NOT EXISTS wushi.market_overview_snapshot
(
    judgement_id String COMMENT '总览判断 ID',
    trade_date Date COMMENT '被展示交易日',
    as_of_date Date COMMENT '站在哪一天观察该交易日',
    judgement_mode String COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    market_cycle_stage String COMMENT '市场周期阶段',
    emotion_cycle_stage String COMMENT '情绪周期阶段',
    primary_mainline_code String COMMENT '第一主线代码',
    primary_mainline_name String COMMENT '第一主线名称',
    market_leader_code String COMMENT '市场总龙代码',
    market_leader_name String COMMENT '市场总龙名称',
    divergence_state String COMMENT '市场分歧一致状态',
    risk_level String COMMENT '市场风险等级',
    confidence Decimal(10, 4) COMMENT '总览置信度',
    rule_version String COMMENT '规则版本',
    data_quality_level String COMMENT '数据质量等级',
    summary String COMMENT '市场总览摘要',
    created_at DateTime DEFAULT now() COMMENT '创建时间'
)
ENGINE = ReplacingMergeTree(created_at)
PARTITION BY toYYYYMM(trade_date)
ORDER BY (trade_date, as_of_date, judgement_mode, judgement_id)
COMMENT '市场总览聚合快照表';
