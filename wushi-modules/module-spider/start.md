配置（可选，有默认值）
在 application.yml 里加：
spider:
ths:
browser:
# 如果有远端 Selenium Docker，取消注释：
# remote-url: http://selenium-chrome:4444/wd/hub
headless: true
proxy-pool:
- "你的代理IP:端口"
- "另一个代理IP:端口"
direct-first: true



4. 启动后端 + 触发跑批
   启动 Spring Boot 后，浏览器或 curl 调用：
# 东财全量（股票日K + 板块 + 个股关系 + 涨跌停 + 所有股票池）
curl "http://localhost:8080/api/spider/dc/daily?tradeDate=2026-07-08"

# 东财仅股票池（涨停/跌停/强势/连板/炸板）
curl "http://localhost:8080/api/spider/dc/pools?tradeDate=2026-07-08"

# 同花顺（板块 + 板块个股关系）
curl "http://localhost:8080/api/spider/ths/daily?tradeDate=2026-07-08"



5. 验证数据
# 查东财抓了多少条股票日K
clickhouse-client --query="SELECT count() FROM wushi.stock_daily_kline WHERE trade_date='2026-07-08'"

# 查涨停池
clickhouse-client --query="SELECT count() FROM wushi.stock_pool_daily_snapshot WHERE trade_date='2026-07-08' AND pool_type='LIMIT_UP'"

# 查板块
clickhouse-client --query="SELECT count() FROM wushi.stock_plate_dimension"



关键注意事项
事项	说明
测试先行	东财API可直接用 docs/test-api.sh 验证，确认接口通再跑批
滑块/反爬	同花顺需 Selenium + 代理池，直连会被封IP
断点续传	跑批中断后重调同一接口，已完成的会跳过（通过 spider_task_checkpoint 表）
数据去重	ClickHouse 用 ReplacingMergeTree，同一 (trade_date, stock_code) 重复跑会覆盖
source字段	1 = 东财, 0 = 同花顺