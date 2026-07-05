# MySQL DDL

这里放置悟势一期 MySQL 配置、人工修正、经验成长、爬虫审计、页面配置相关 DDL。

建议执行顺序：

1. `001_rule_config.sql`：规则版本、因子定义、因子权重、组合因子、数据质量影响配置。
2. `002_manual_review.sql`：人工修正、证据标注、历史样本确认。
3. `003_experience_growth.sql`：前向验证、因子经验、组合经验、成长日志。
4. `004_spider_audit.sql`：爬虫任务检查点、同步审计、数据质量审计。
5. `005_dashboard_config.sql`：页面卡片配置表。
6. `006_seed_rule_config.sql`：一期默认规则、因子、组合条件、数据质量扣分和页面卡片种子数据。
