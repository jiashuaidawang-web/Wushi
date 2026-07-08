-- 2026-07-09: stock_pool_daily_snapshot 新增 pool_type 字段，区分涨停/跌停/强势/连板/炸板
ALTER TABLE wushi.stock_pool_daily_snapshot
    ADD COLUMN IF NOT EXISTS pool_type String COMMENT '股票池类型，LIMIT_UP/LIMIT_DOWN/STRONG/SUB_NEW/BROKEN/YEST_LIMIT_UP' AFTER suspend_status;
