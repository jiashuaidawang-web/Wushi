USE wushi;

DELIMITER //
DROP PROCEDURE IF EXISTS migrate_rule_candidate_columns//
CREATE PROCEDURE migrate_rule_candidate_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_version' AND COLUMN_NAME = 'source_rule_version'
    ) THEN
        ALTER TABLE rule_version ADD COLUMN source_rule_version VARCHAR(64) NULL COMMENT '候选版本来源规则版本' AFTER description;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_version' AND COLUMN_NAME = 'candidate_stat_date'
    ) THEN
        ALTER TABLE rule_version ADD COLUMN candidate_stat_date DATE NULL COMMENT '生成候选版本的经验统计日期' AFTER source_rule_version;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_version' AND COLUMN_NAME = 'approved_by'
    ) THEN
        ALTER TABLE rule_version ADD COLUMN approved_by VARCHAR(64) NULL COMMENT '批准或拒绝人' AFTER effective_date;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_version' AND COLUMN_NAME = 'approved_at'
    ) THEN
        ALTER TABLE rule_version ADD COLUMN approved_at DATETIME NULL COMMENT '批准或拒绝时间' AFTER approved_by;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_version' AND COLUMN_NAME = 'approval_remark'
    ) THEN
        ALTER TABLE rule_version ADD COLUMN approval_remark TEXT NULL COMMENT '批准或拒绝备注' AFTER approved_at;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rule_version' AND INDEX_NAME = 'idx_rule_candidate'
    ) THEN
        ALTER TABLE rule_version ADD INDEX idx_rule_candidate (engine_type, status, source_rule_version, candidate_stat_date);
    END IF;
END//
DELIMITER ;

CALL migrate_rule_candidate_columns();
DROP PROCEDURE migrate_rule_candidate_columns;

CREATE TABLE IF NOT EXISTS factor_performance_stat
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本',
    engine_type VARCHAR(64) NOT NULL COMMENT '引擎类型',
    factor_code VARCHAR(128) NOT NULL COMMENT '因子代码',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '样本数量',
    hit_count INT NOT NULL DEFAULT 0 COMMENT '命中数量',
    miss_count INT NOT NULL DEFAULT 0 COMMENT '未命中数量',
    conflict_hit_count INT NOT NULL DEFAULT 0 COMMENT '冲突证据命中数量',
    hit_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '命中率',
    avg_contribution_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '平均贡献分',
    suggested_weight_delta DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '建议权重调整',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_factor_stat (stat_date, rule_version, engine_type, factor_code)
) COMMENT='单因子历史表现统计表';

CREATE TABLE IF NOT EXISTS factor_combination_performance_stat
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本',
    engine_type VARCHAR(64) NOT NULL COMMENT '引擎类型',
    combination_code VARCHAR(128) NOT NULL COMMENT '组合因子代码',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '样本数量',
    hit_count INT NOT NULL DEFAULT 0 COMMENT '命中数量',
    miss_count INT NOT NULL DEFAULT 0 COMMENT '未命中数量',
    hit_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '命中率',
    avg_forward_return DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '平均后验收益率',
    avg_drawdown DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '平均后验最大回撤',
    suggested_action VARCHAR(64) NULL COMMENT '建议动作，INCREASE_WEIGHT/DECREASE_WEIGHT/KEEP/DISABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_combination_stat (stat_date, rule_version, engine_type, combination_code)
) COMMENT='组合因子历史表现统计表';

CREATE TABLE IF NOT EXISTS factor_combination_trigger_record
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    trigger_id VARCHAR(64) NOT NULL COMMENT '触发记录 ID',
    trade_date DATE NOT NULL COMMENT '触发交易日',
    judgement_id VARCHAR(64) NOT NULL COMMENT '判断 ID',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本',
    engine_type VARCHAR(64) NOT NULL COMMENT '引擎类型',
    target_type VARCHAR(64) NOT NULL COMMENT '对象类型',
    target_code VARCHAR(64) NOT NULL COMMENT '对象代码',
    combination_code VARCHAR(128) NOT NULL COMMENT '组合因子代码',
    trigger_values JSON NOT NULL COMMENT '触发时因子值',
    validation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '验证状态，PENDING/HIT/MISS/INSUFFICIENT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_trigger_id (trigger_id),
    KEY idx_trade_combination (trade_date, combination_code)
) COMMENT='组合因子触发记录表';

CREATE TABLE IF NOT EXISTS system_growth_log
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    growth_id VARCHAR(64) NOT NULL COMMENT '成长日志 ID',
    trade_date DATE NOT NULL COMMENT '成长归属交易日',
    growth_type VARCHAR(64) NOT NULL COMMENT '成长类型，FACTOR/COMBINATION/RULE/MANUAL',
    engine_type VARCHAR(64) NULL COMMENT '关联引擎类型',
    title VARCHAR(255) NOT NULL COMMENT '日志标题',
    content TEXT NOT NULL COMMENT '日志内容',
    before_value TEXT NULL COMMENT '变化前值',
    after_value TEXT NULL COMMENT '变化后值',
    source_ref VARCHAR(128) NULL COMMENT '来源引用，例如验证 ID 或修正 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_growth_id (growth_id),
    KEY idx_trade_type (trade_date, growth_type)
) COMMENT='系统成长日志表';

CREATE TABLE IF NOT EXISTS daily_review_archive
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    archive_id VARCHAR(64) NOT NULL COMMENT '归档 ID',
    trade_date DATE NOT NULL COMMENT '复盘交易日',
    as_of_date DATE NOT NULL COMMENT '观察日期',
    judgement_mode VARCHAR(32) NOT NULL COMMENT '判断模式',
    overview_summary TEXT NOT NULL COMMENT '市场总览摘要',
    cycle_summary TEXT NULL COMMENT '周期复盘摘要',
    mainline_summary TEXT NULL COMMENT '主线复盘摘要',
    leader_summary TEXT NULL COMMENT '龙头复盘摘要',
    risk_summary TEXT NULL COMMENT '风险复盘摘要',
    next_watch_summary TEXT NULL COMMENT '下一交易日观察摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_archive (trade_date, as_of_date, judgement_mode)
) COMMENT='每日复盘归档表';
