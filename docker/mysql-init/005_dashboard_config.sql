USE wushi;

CREATE TABLE IF NOT EXISTS dashboard_card_config
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    page_code VARCHAR(128) NOT NULL COMMENT '页面代码',
    card_code VARCHAR(128) NOT NULL COMMENT '卡片代码',
    card_name VARCHAR(128) NOT NULL COMMENT '卡片名称',
    card_type VARCHAR(64) NOT NULL COMMENT '卡片类型，JUDGEMENT/CHART/TABLE/LIST',
    data_api VARCHAR(255) NOT NULL COMMENT '数据接口路径',
    display_order INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    required_fields JSON NULL COMMENT '卡片依赖字段',
    thought_mapping TEXT NULL COMMENT '该卡片表达的情绪周期思想',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用，1 是，0 否',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_page_card (page_code, card_code)
) COMMENT='页面卡片配置表';
