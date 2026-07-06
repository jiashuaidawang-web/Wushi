USE wushi;

CREATE TABLE IF NOT EXISTS rule_version_candidate
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    candidate_id VARCHAR(64) NOT NULL COMMENT '候选版本 ID',
    base_rule_version VARCHAR(64) NOT NULL COMMENT '基准规则版本',
    target_rule_version VARCHAR(64) NOT NULL COMMENT '候选目标规则版本',
    engine_type VARCHAR(64) NOT NULL COMMENT '引擎类型',
    status VARCHAR(32) NOT NULL COMMENT '候选状态，GENERATED/PENDING_APPROVAL/APPROVED/REJECTED/EFFECTIVE/CANCELLED',
    stat_date DATE NOT NULL COMMENT '经验统计日期',
    factor_change_count INT NOT NULL DEFAULT 0 COMMENT '发生调整的因子数量',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '候选版本采用的总样本数量',
    total_abs_delta DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '本次候选版本权重绝对调整总量',
    reason_summary TEXT NOT NULL COMMENT '生成原因摘要',
    risk_summary TEXT NULL COMMENT '风险提示摘要',
    generated_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '生成人',
    approved_by VARCHAR(64) NULL COMMENT '审批人',
    approval_comment TEXT NULL COMMENT '审批意见',
    approved_at DATETIME NULL COMMENT '审批时间',
    effective_at DATETIME NULL COMMENT '生效时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_candidate_id (candidate_id),
    UNIQUE KEY uk_target_engine (target_rule_version, engine_type),
    KEY idx_base_engine_stat (base_rule_version, engine_type, stat_date),
    KEY idx_status_created (status, created_at)
) COMMENT='规则版本演进候选表';

CREATE TABLE IF NOT EXISTS rule_version_candidate_factor
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    candidate_id VARCHAR(64) NOT NULL COMMENT '候选版本 ID',
    engine_type VARCHAR(64) NOT NULL COMMENT '引擎类型',
    factor_code VARCHAR(128) NOT NULL COMMENT '因子代码',
    factor_name VARCHAR(128) NULL COMMENT '因子名称',
    current_weight DECIMAL(10,4) NOT NULL COMMENT '当前规则权重',
    suggested_delta DECIMAL(10,4) NOT NULL COMMENT '经验建议调整量',
    suggested_weight DECIMAL(10,4) NOT NULL COMMENT '候选版本建议权重',
    threshold_value DECIMAL(18,6) NULL COMMENT '继承的阈值',
    threshold_operator VARCHAR(16) NULL COMMENT '继承的阈值操作符',
    evidence_type VARCHAR(32) NOT NULL COMMENT '继承的证据类型',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '经验样本数',
    hit_count INT NOT NULL DEFAULT 0 COMMENT '验证命中的样本数',
    miss_count INT NOT NULL DEFAULT 0 COMMENT '验证未命中的样本数',
    conflict_hit_count INT NOT NULL DEFAULT 0 COMMENT '冲突证据最终被市场验证命中的样本数',
    hit_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '经验命中率',
    avg_contribution_score DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '平均贡献分',
    suggested_action VARCHAR(64) NOT NULL COMMENT '建议动作，INCREASE_WEIGHT/DECREASE_WEIGHT/KEEP/NEED_MANUAL_REVIEW',
    change_reason TEXT NOT NULL COMMENT '调整原因，必须说明证据奖惩来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_candidate_factor (candidate_id, engine_type, factor_code),
    KEY idx_candidate (candidate_id)
) COMMENT='规则版本候选因子调整明细表';
