# Wushi Docker 完全独立部署

## 架构

```
┌─────────────────────────────────────────────────────────┐
│ Docker Compose (wushi_net)                              │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐│
│  │ MySQL    │  │ClickHouse│  │ Selenium │  │ Backend ││
│  │ :3306    │  │ :8123    │  │ :4444    │  │ :8080   ││
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬────┘│
│       └──────────────┴─────────────┴─────────────┘      │
└─────────────────────────────────────────────────────────┘
```

## 端口映射

| 服务 | 容器端口 | 宿主机端口 | 协议 | 用途 |
|------|---------|-----------|------|------|
| MySQL | 3306 | **3307** | TCP | 业务数据库 |
| ClickHouse | 8123 | **8124** | HTTP | SQL 接口 |
| ClickHouse | 9000 | **9001** | Native | 高性能接口 |
| Selenium | 4444 | **4445** | HTTP | 浏览器自动化 |
| Backend | 8080 | **8081** | HTTP | Web API |

宿主机端口已做偏移, 避免与现有服务冲突。

## 部署步骤

```bash
# 1. 进目录
cd /path/to/Wushi/docker

# 2. 一键部署
./deploy.sh
```

或手动:

```bash
docker compose build backend
docker compose up -d
docker compose logs -f backend
```

## 数据库

- **MySQL**: 首次启动自动建库 `wushi` + 8 个 SQL 文件按 001→008 顺序执行 → 21 张表
- **ClickHouse**: 首次启动自动建库 `wushi` + `001_all_tables.sql` → 29 张表

## 运维

```bash
# 看日志
docker compose logs -f backend
docker compose logs -f clickhouse
docker compose logs -f mysql

# 服务状态
docker compose ps

# 重启
docker compose restart backend

# 停服
docker compose down

# 完全清理 (含数据卷, 数据全丢)
docker compose down -v

# 跑批测试
curl 'http://localhost:8081/api/spider/dc/daily?tradeDate=2026-07-10'
```

## 数据持久化

| 卷名 | 内容 |
|------|------|
| mysql_data | MySQL 数据 |
| clickhouse_data | ClickHouse 数据 |
| clickhouse_log | ClickHouse 日志 |

## 目录结构

```
docker/
├── README.md             # 本文件
├── deploy.sh             # 一键部署脚本
├── mysql-init/           # MySQL 21 张表 SQL (按 001~008 顺序执行)
│   ├── 001_rule_config.sql      (5 表)
│   ├── 002_manual_review.sql     (4 表)
│   ├── 003_experience_growth.sql (5 表)
│   ├── 004_spider_audit.sql      (4 表)
│   ├── 005_dashboard_config.sql  (1 表)
│   ├── 006_seed_rule_config.sql  (seed 数据)
│   ├── 007_rule_evolution.sql    (2 表)
│   └── 008_fix_rule_version_columns.sql (ALTER)
└── clickhouse-init/      # ClickHouse 29 张表 DDL
    ├── 00_init.sh
    └── 001_all_tables.sql
```
