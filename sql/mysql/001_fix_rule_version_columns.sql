-- ============================================================
-- 修复 rule_version 表缺失的列
-- 错误: Unknown column 'source_rule_version' in 'field list'
-- 执行方式: mysql -u root -p wushi < 001_fix_rule_version_columns.sql
-- ============================================================

USE wushi;

-- 使用存储过程安全地添加列（幂等，可重复执行）
DELIMITER //

DROP PROCEDURE IF EXISTS add_column_if_not_exists//
CREATE PROCEDURE add_column_if_not_exists(
    IN tbl VARCHAR(64),
    IN col VARCHAR(64),
    IN col_def VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN ', col, ' ', col_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS add_index_if_not_exists//
CREATE PROCEDURE add_index_if_not_exists(
    IN tbl VARCHAR(64),
    IN idx_name VARCHAR(64),
    IN idx_def VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND INDEX_NAME = idx_name
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE ', tbl, ' ADD INDEX ', idx_name, ' ', idx_def);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

-- 添加缺失的列
CALL add_column_if_not_exists('rule_version', 'source_rule_version', 'VARCHAR(64) NULL COMMENT ''候选版本来源规则版本'' AFTER description');
CALL add_column_if_not_exists('rule_version', 'candidate_stat_date', 'DATE NULL COMMENT ''生成候选版本的经验统计日期'' AFTER source_rule_version');
CALL add_column_if_not_exists('rule_version', 'approved_by', 'VARCHAR(64) NULL COMMENT ''批准或拒绝人'' AFTER effective_date');
CALL add_column_if_not_exists('rule_version', 'approved_at', 'DATETIME NULL COMMENT ''批准或拒绝时间'' AFTER approved_by');
CALL add_column_if_not_exists('rule_version', 'approval_remark', 'TEXT NULL COMMENT ''批准或拒绝备注'' AFTER approved_at');

-- 添加缺失的索引
CALL add_index_if_not_exists('rule_version', 'idx_rule_candidate', '(engine_type, status, source_rule_version, candidate_stat_date)');

-- 清理存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

-- 验证结果
SELECT 'rule_version columns after fix:' AS message;
SHOW COLUMNS FROM rule_version;
