# 后端工程初始化说明

当前工程已调整为根目录基础层 + `wushi-modules` 业务模块层。

## 已完成

- Maven 多模块父工程。
- Spring Boot 3 启动模块。
- Log4j2 基础配置。
- MySQL / ClickHouse 数据源配置。
- 统一 API 响应。
- 统一业务异常。
- 健康检查接口。
- 核心判断模型 `JudgementResult<T>`。
- 证据模型 `EvidenceItem`。
- 明日观察点模型 `NextWatchItem`。
- 任务编排接口 `EngineTask`。
- 任务编排器 `EngineBatchOrchestrator`。

## 下一步

1. 落地一期完整 DDL。
2. 为 MySQL / ClickHouse 建立基础 Repository。
3. 补齐跑批日志模型。
4. 实现第一批空任务链，先跑通编排。
5. 开始事实数据写入与查询。
