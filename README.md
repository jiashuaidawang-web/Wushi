# 悟势 Wushi

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
