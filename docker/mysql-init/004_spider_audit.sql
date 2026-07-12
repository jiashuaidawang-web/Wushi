USE wushi;

CREATE TABLE IF NOT EXISTS spider_task_checkpoint
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    task_code VARCHAR(128) NOT NULL COMMENT '采集任务代码',
    task_name VARCHAR(128) NOT NULL COMMENT '采集任务名称',
    trade_date DATE NOT NULL COMMENT '采集交易日',
    provider VARCHAR(64) NOT NULL COMMENT '数据源供应商',
    checkpoint_value TEXT NULL COMMENT '断点值，例如页码、游标、最后代码',
    status VARCHAR(32) NOT NULL COMMENT '状态，PENDING/RUNNING/SUCCESS/FAILED/PARTIAL',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功数量',
    fail_count INT NOT NULL DEFAULT 0 COMMENT '失败数量',
    error_message TEXT NULL COMMENT '错误信息',
    started_at DATETIME NULL COMMENT '开始时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_task_trade_provider (task_code, trade_date, provider)
) COMMENT='爬虫任务断点表';

CREATE TABLE IF NOT EXISTS data_sync_audit_log
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    audit_id VARCHAR(64) NOT NULL COMMENT '审计 ID',
    task_code VARCHAR(128) NOT NULL COMMENT '任务代码',
    trade_date DATE NOT NULL COMMENT '交易日期',
    provider VARCHAR(64) NOT NULL COMMENT '数据源供应商',
    target_table VARCHAR(128) NOT NULL COMMENT '写入目标表',
    sync_status VARCHAR(32) NOT NULL COMMENT '同步状态，SUCCESS/FAILED/PARTIAL/SKIPPED',
    fetched_count INT NOT NULL DEFAULT 0 COMMENT '抓取数量',
    inserted_count INT NOT NULL DEFAULT 0 COMMENT '写入数量',
    updated_count INT NOT NULL DEFAULT 0 COMMENT '更新数量',
    failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数量',
    data_start_time DATETIME NULL COMMENT '数据起始时间',
    data_end_time DATETIME NULL COMMENT '数据结束时间',
    error_message TEXT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_audit_id (audit_id),
    KEY idx_trade_task (trade_date, task_code)
) COMMENT='数据同步审计日志表';

CREATE TABLE IF NOT EXISTS engine_batch_run_log
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    batch_id VARCHAR(64) NOT NULL COMMENT '批次 ID',
    trade_date DATE NOT NULL COMMENT '跑批交易日',
    as_of_date DATE NOT NULL COMMENT '观察日期',
    judgement_mode VARCHAR(32) NOT NULL COMMENT '判断模式',
    run_mode VARCHAR(32) NOT NULL COMMENT '运行模式，DAILY/HISTORY_REPLAY/MANUAL',
    rule_version VARCHAR(64) NOT NULL COMMENT '规则版本',
    status VARCHAR(32) NOT NULL COMMENT '状态，RUNNING/SUCCESS/FAILED/PARTIAL',
    started_at DATETIME NOT NULL COMMENT '开始时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    error_message TEXT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_batch_id (batch_id),
    KEY idx_trade_mode (trade_date, judgement_mode, run_mode)
) COMMENT='引擎跑批主日志表';

CREATE TABLE IF NOT EXISTS engine_batch_step_log
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    batch_id VARCHAR(64) NOT NULL COMMENT '批次 ID',
    step_name VARCHAR(128) NOT NULL COMMENT '步骤名称',
    step_order INT NOT NULL COMMENT '步骤顺序',
    status VARCHAR(32) NOT NULL COMMENT '状态，PENDING/RUNNING/SUCCESS/FAILED/SKIPPED',
    affected_rows BIGINT NOT NULL DEFAULT 0 COMMENT '影响行数',
    started_at DATETIME NULL COMMENT '开始时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    error_message TEXT NULL COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_batch_step (batch_id, step_name),
    KEY idx_batch_order (batch_id, step_order)
) COMMENT='引擎跑批步骤日志表';
