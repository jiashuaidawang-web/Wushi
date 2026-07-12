#!/bin/bash
# ClickHouse Docker 首次启动初始化
# 注意: ClickHouse 官方 docker image 会自动执行 /docker-entrypoint-initdb.d/ 里的 .sh 和 .sql
# 这个脚本用来做额外的配置 (用户/权限), 避免 SQL 文件里的 GRANT 语法问题
set -e

echo "[wushi] ClickHouse init: 等待服务就绪..."
for i in $(seq 1 30); do
  if clickhouse-client --query "SELECT 1" &>/dev/null; then
    echo "[wushi] ClickHouse 已就绪"
    break
  fi
  sleep 2
done

# 确保 wushi 数据库存在 (001_all_tables.sql 会 CREATE, 但这里双重保险)
clickhouse-client --query "CREATE DATABASE IF NOT EXISTS wushi"

echo "[wushi] ClickHouse init done."
