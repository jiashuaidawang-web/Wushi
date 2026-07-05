USE wushi;

CREATE TABLE IF NOT EXISTS manual_correction_record
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    correction_id VARCHAR(64) NOT NULL COMMENT '修正 ID',
    trade_date DATE NOT NULL COMMENT '被修正交易日',
    as_of_date DATE NOT NULL COMMENT '修正对应观察日期',
    judgement_mode VARCHAR(32) NOT NULL COMMENT '判断模式，REALTIME/RETROSPECTIVE',
    engine_type VARCHAR(64) NOT NULL COMMENT '被修正引擎类型',
    target_type VARCHAR(64) NOT NULL COMMENT '对象类型',
    target_code VARCHAR(64) NOT NULL COMMENT '对象代码',
    judgement_id VARCHAR(64) NULL COMMENT '原判断 ID',
    correction_type VARCHAR(64) NOT NULL COMMENT '修正类型，CONCLUSION/EVIDENCE/RISK/SAMPLE',
    correction_reason TEXT NOT NULL COMMENT '修正原因',
    reviewer VARCHAR(64) NOT NULL COMMENT '修正人',
    status VARCHAR(32) NOT NULL DEFAULT 'EFFECTIVE' COMMENT '状态，EFFECTIVE/REVOKED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_correction_id (correction_id),
    KEY idx_trade_engine_target (trade_date, engine_type, target_type, target_code)
) COMMENT='人工修正记录表';

CREATE TABLE IF NOT EXISTS manual_correction_item
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    correction_id VARCHAR(64) NOT NULL COMMENT '修正 ID',
    field_name VARCHAR(128) NOT NULL COMMENT '被修正字段名',
    old_value TEXT NULL COMMENT '修正前值',
    new_value TEXT NOT NULL COMMENT '修正后值',
    field_desc VARCHAR(255) NULL COMMENT '字段说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_correction_id (correction_id)
) COMMENT='人工修正字段明细表';

CREATE TABLE IF NOT EXISTS evidence_manual_label
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    label_id VARCHAR(64) NOT NULL COMMENT '证据标注 ID',
    evidence_id VARCHAR(64) NOT NULL COMMENT '证据 ID',
    judgement_id VARCHAR(64) NOT NULL COMMENT '判断 ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    label_result VARCHAR(32) NOT NULL COMMENT '标注结果，VALID/INVALID/OVER_WEIGHTED/UNDER_WEIGHTED',
    label_reason TEXT NOT NULL COMMENT '标注原因',
    reviewer VARCHAR(64) NOT NULL COMMENT '标注人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_label_id (label_id),
    KEY idx_evidence (evidence_id),
    KEY idx_judgement (judgement_id)
) COMMENT='证据人工标注表';

CREATE TABLE IF NOT EXISTS historical_sample_confirmation
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    sample_id VARCHAR(64) NOT NULL COMMENT '样本 ID',
    trade_date DATE NOT NULL COMMENT '样本交易日',
    target_type VARCHAR(64) NOT NULL COMMENT '样本对象类型',
    target_code VARCHAR(64) NOT NULL COMMENT '样本对象代码',
    sample_type VARCHAR(64) NOT NULL COMMENT '样本类型，CYCLE/MAINLINE/LEADER/PATTERN/RISK',
    confirmed_label VARCHAR(128) NOT NULL COMMENT '人工确认标签',
    sample_quality VARCHAR(32) NOT NULL COMMENT '样本质量，HIGH/MEDIUM/LOW',
    confirmation_desc TEXT NULL COMMENT '确认说明',
    reviewer VARCHAR(64) NOT NULL COMMENT '确认人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sample_id (sample_id),
    KEY idx_trade_sample (trade_date, sample_type, target_code)
) COMMENT='历史样本人工确认表';
