# Provider 实现约定

具体数据源实现类放在 `provider/{source}` 包下，例如：

- `provider/eastmoney/EastMoneyStockKlineProvider`
- `provider/ths/ThsPlateKlineProvider`

实现类只负责抓取和解析，产出 `ClickHouseRow`。写入 ClickHouse 统一走 `SpiderIngestionService`，避免爬虫实现直接操作数据库。
