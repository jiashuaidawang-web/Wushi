CREATE DATABASE IF NOT EXISTS wushi DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE wushi;

CREATE TABLE IF NOT EXISTS rule_version
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本号',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    engine_type VARCHAR(64) NOT NULL COMMENT '适用引擎类型',
    status VARCHAR(32) NOT NULL COMMENT '状态，DRAFT/CANDIDATE/ACTIVE/ARCHIVED/REJECTED',
    description TEXT NULL COMMENT '规则说明',
    source_rule_version VARCHAR(64) NULL COMMENT '候选版本来源规则版本',
    candidate_stat_date DATE NULL COMMENT '生成候选版本的经验统计日期',
    effective_date DATE NULL COMMENT '生效日期',
    approved_by VARCHAR(64) NULL COMMENT '批准或拒绝人',
    approved_at DATETIME NULL COMMENT '批准或拒绝时间',
    approval_remark TEXT NULL COMMENT '批准或拒绝备注',
    created_by VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_rule_version (rule_version, engine_type),
    KEY idx_rule_candidate (engine_type, status, source_rule_version, candidate_stat_date)
) COMMENT='规则版本表';

CREATE TABLE IF NOT EXISTS factor_definition
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    factor_code VARCHAR(128) NOT NULL COMMENT '因子代码',
    factor_name VARCHAR(128) NOT NULL COMMENT '因子名称',
    engine_type VARCHAR(64) NOT NULL COMMENT '所属引擎类型',
    factor_direction VARCHAR(32) NOT NULL COMMENT '因子方向，POSITIVE/NEGATIVE/NEUTRAL',
    factor_desc TEXT NULL COMMENT '因子说明',
    source_table VARCHAR(128) NULL COMMENT '默认来源表',
    value_type VARCHAR(32) NOT NULL COMMENT '值类型，NUMBER/RATIO/BOOLEAN/ENUM',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用，1 是，0 否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_factor_code (factor_code)
) COMMENT='因子定义表';

CREATE TABLE IF NOT EXISTS rule_factor_weight
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本号',
    engine_type VARCHAR(64) NOT NULL COMMENT '引擎类型',
    factor_code VARCHAR(128) NOT NULL COMMENT '因子代码',
    weight DECIMAL(10,4) NOT NULL COMMENT '因子权重',
    threshold_value DECIMAL(18,6) NULL COMMENT '阈值',
    threshold_operator VARCHAR(16) NULL COMMENT '阈值操作符，GT/GTE/LT/LTE/EQ/BETWEEN',
    evidence_type VARCHAR(32) NOT NULL COMMENT '默认证据类型，SUPPORT/CONFLICT/WARNING',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用，1 是，0 否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_rule_factor (rule_version, engine_type, factor_code)
) COMMENT='规则因子权重表';

CREATE TABLE IF NOT EXISTS factor_combination_definition
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    combination_code VARCHAR(128) NOT NULL COMMENT '组合因子代码',
    combination_name VARCHAR(128) NOT NULL COMMENT '组合因子名称',
    engine_type VARCHAR(64) NOT NULL COMMENT '所属引擎类型',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本',
    factor_codes JSON NOT NULL COMMENT '组合内因子代码列表',
    condition_expression TEXT NOT NULL COMMENT '组合触发条件表达式',
    expected_meaning TEXT NULL COMMENT '组合触发后的市场含义',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用，1 是，0 否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_combination_code_version (combination_code, rule_version)
) COMMENT='组合因子定义表';

CREATE TABLE IF NOT EXISTS data_quality_impact_config
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    data_domain VARCHAR(64) NOT NULL COMMENT '数据域',
    table_name VARCHAR(128) NOT NULL COMMENT '表名',
    missing_field VARCHAR(128) NULL COMMENT '缺失字段',
    impact_pages JSON NOT NULL COMMENT '影响页面列表',
    confidence_penalty DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '置信度扣减',
    impact_desc TEXT NULL COMMENT '影响说明',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用，1 是，0 否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_domain_table (data_domain, table_name)
) COMMENT='数据质量影响配置表';
