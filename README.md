# 悟势 Wushi

后端:
1. WebDriver 池必须支持 invalidateObject，不允许异常浏览器回池
2. 所有爬虫请求必须经过 Resilience4j 的 retry/rateLimiter/circuitBreaker
3. Selenium 并发由 WebDriverPool 控制，不能因虚拟线程无限放大
4. ClickHouse 查询和写入单独封装，不和 MySQL Cursor 逻辑混用

1. 规则先配置化，不先机器学习黑盒化
2. ClickHouse 存事实和推演结果，MySQL 存配置和人工闭环
3. 所有判断结果落库，不只实时算
4. 页面优先查快照结果，避免每次实时重算
5. 引擎支持手动重算和历史回放
6. evidence/conflict/warning 作为一等数据结构

Kafka：暂时没必要
Flink：暂时没必要
Elasticsearch：除非后面做新闻/公告全文检索
Python训练服务：后面有足够样本再说
微服务拆太细：一期会拖慢交付


src/api          接口
src/stores       全局日期/视角/复盘状态
src/views        九大页面
src/components   通用卡片、证据抽屉、数据质量抽屉
src/types        VO类型
src/utils        格式化、枚举映射


Spring Boot 3 + JDK 21：认可
MyBatis-Plus：认可
Log4j2：认可
Selenium 4 RemoteWebDriver：认可
浏览器独立 Docker：强烈认可
Caffeine：认可
Resilience4j：认可
HikariCP：认可

1. JDK 21 虚拟线程适合大量 I/O 爬虫和批处理
2. Selenium 独立容器能隔离 Chrome 资源
3. Resilience4j 能处理爬虫重试、限流、熔断
4. Caffeine 适合缓存规则、因子、交易日历、板块字典
   第一，WebDriver 池要谨慎。
   浏览器 Session 不是普通 JDBC 连接，池化可以做，但不能太激进。因为同花顺这类站点可能出现：
   页面状态污染
   Cookie污染
   反爬状态继承
   Session假活
   内存逐渐膨胀
   所以我建议：
   WebDriverPool 可以用，但要有最大使用次数和最大存活时间
   借出前不仅 testOnBorrow，还要做轻量健康检查
   发现 forbidden / captcha / 页面异常后，不是 returnObject，而是 invalidateObject
   否则一个“脏浏览器”会反复污染后续任务。
   第二，testOnBorrow(true) 还不够。
   需要实现：
   validateObject()
   检查：
   Session 是否存在
   当前窗口是否可用
   远端 Chrome 是否响应
   是否命中过反爬页
   第三，finally 不能无脑 returnObject(driver)。
   要区分：
   正常成功 -> returnObject
   浏览器崩溃 -> invalidateObject
   403/滑块/forbidden -> invalidateObject 或标记代理降权
   伪代码应该是：
   boolean broken = false;
   try {
   driver = pool.borrowObject();
   // crawl
   if (antiBotDetected) {
   broken = true;
   throw new AntiBotException();
   }
   } finally {
   if (driver != null) {
   if (broken) pool.invalidateObject(driver);
   else pool.returnObject(driver);
   }
   }
   第四，虚拟线程适合 I/O，但不代表浏览器可以无限并发。
   实际并发瓶颈在：
   Chrome 容器CPU
   Chrome容器内存
   目标站点反爬
   代理质量
   ClickHouse写入吞吐
   所以并发控制应该是两层：
   虚拟线程：负责轻量任务调度
   WebDriverPool maxTotal：控制浏览器并发上限
   Resilience4j RateLimiter：控制站点请求频率
   第五，MyBatis-Plus Cursor 适合 MySQL，不要想当然用于 ClickHouse。
   我们这个系统里：
   MySQL：规则、配置、修正、审计，数据量不大
   ClickHouse：行情和快照，大查询多
   MP Cursor 主要用于 MySQL 大量读取，比如历史样本、任务记录。
   ClickHouse 查询建议使用：
   clickhouse-jdbc
   分页/分区查询
   按 trade_date 范围批处理
   避免一次查全历史
   第六，Log4j2 可以，但要注意 Spring Boot 3 默认 Logback，要彻底排除。
   需要：
   排除 spring-boot-starter-logging
   引入 spring-boot-starter-log4j2
   统一 log4j2-spring.xml
   不然会出现日志桥接混乱。
   第七，Resilience4j 要按“数据源/站点/代理”维度配置，不要一个全局配置。
   建议：
   thsPageCrawlerRetry
   thsApiCrawlerRetry
   eastMoneyCrawlerRetry
   seleniumRemoteRetry
   proxyProviderCircuitBreaker
   clickHouseWriteRetry
   不同源失败模式不同，不能一把梭。
   我最终建议的一期技术栈
   JDK 21
   Spring Boot 3.x
   Maven 多模块
   MyBatis-Plus 3.5.x
   MySQL + HikariCP
   ClickHouse JDBC
   Log4j2
   Caffeine
   Resilience4j
   Selenium 4 RemoteWebDriver
   Apache Commons Pool2
   XXL-JOB 或 Spring Scheduler
   Vue 3 + Vite + TypeScript + Pinia + Element Plus + ECharts
   任务编排
   还是坚持：
   不引入 Flowable / Camunda
   使用 XXL-JOB/Scheduler + 自研 EngineBatchOrchestrator
   因为我们是计算 DAG，不是审批流。
   我的架构结论
   这套方案可以用，但我要加 4 个硬约束：
1. WebDriver 池必须支持 invalidateObject，不允许异常浏览器回池
2. 所有爬虫请求必须经过 Resilience4j 的 retry/rateLimiter/circuitBreaker
3. Selenium 并发由 WebDriverPool 控制，不能因虚拟线程无限放大
4. ClickHouse 查询和写入单独封装，不和 MySQL Cursor 逻辑混用

前端我建议定成：
Vue 3 + Vite + TypeScript + Pinia + Vue Router + Element Plus + ECharts

悟势是基于情绪周期理论设计的市场推演沙盘。系统目标不是给市场贴静态标签，而是表达市场如何演化、主线如何形成、龙头如何走出来、分歧如何转一致、风险如何兑现，并通过事后验证和人工修正持续积累经验。

## 工程结构

- `wushi-app`：Spring Boot 启动模块。
- `wushi-api`：页面接口、VO、全局异常处理。
- `wushi-common`：通用响应、枚举、异常、核心判断模型。
- `wushi-infrastructure`：MySQL、ClickHouse、缓存、日志、外部基础设施。
- `wushi-modules/module-market`：行情事实数据、市场宽度、涨停梯队、板块快照。
- `wushi-modules/module-spider`：数据采集、断点、同步审计。
- `wushi-modules/module-rule`：规则版本、因子定义、任务编排。
- `wushi-modules/module-emotion`：市场周期、情绪周期识别。
- `wushi-modules/module-mainline`：主线识别、主线强度、主线状态。
- `wushi-modules/module-leader`：龙头竞争、地位判断、挑战者、掉队。
- `wushi-modules/module-pattern`：分歧一致、回封质量、炸板质量、模式条件。
- `wushi-modules/module-risk`：风险雷达、高位负反馈、退潮风险。
- `wushi-modules/module-similarity`：历史相似、相似因子、后验表现。
- `wushi-modules/module-review`：人工修正、证据标注、样本确认。
- `wushi-modules/module-sample`：历史样本、复盘归档、训练样本沉淀。
- `wushi-modules/module-backtest`：T+1/T+3/T+5/T+10 验证与回测。
- `wushi-modules/module-agent-audit`：Agent 审计、推演日志、解释记录。

## 核心约束

所有推演判断必须携带证据、冲突、置信度和规则版本。系统不能只输出标签。
