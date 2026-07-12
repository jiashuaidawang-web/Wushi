#!/usr/bin/env bash
# ============================================================
# Wushi Docker 完全独立部署脚本
# ============================================================
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# ----------------------------------------------------------------
# 校验
# ----------------------------------------------------------------
if ! command -v docker &>/dev/null; then
  echo "[ERROR] Docker 未安装, 请先安装 Docker"
  exit 1
fi

if docker compose version &>/dev/null; then
  DC="docker compose"
elif docker-compose version &>/dev/null; then
  DC="docker-compose"
else
  echo "[ERROR] docker-compose 未安装"
  exit 1
fi

echo "============================================"
echo "  Wushi 完全独立部署"
echo "============================================"
echo "  Docker: $(docker --version)"
echo "  Compose: $($DC version --short)"
echo ""

# ----------------------------------------------------------------
# 构建
# ----------------------------------------------------------------
echo "[1/4] 构建镜像..."
$DC build --no-cache backend 2>&1 | tail -20

# ----------------------------------------------------------------
# 启动
# ----------------------------------------------------------------
echo ""
echo "[2/4] 启动服务..."
$DC up -d

# ----------------------------------------------------------------
# 等待就绪
# ----------------------------------------------------------------
echo ""
echo "[3/4] 等待服务就绪 (最多 3 分钟)..."

wait_healthy() {
  local name=$1
  local max_wait=180
  local elapsed=0
  while [ $elapsed -lt $max_wait ]; do
    local status
    status=$($DC ps --format json "$name" 2>/dev/null | grep -o '"Health":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "starting")
    if [ "$status" = "healthy" ]; then
      echo "  [$name] ✅ healthy"
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
    printf "\r  等待 %s... %ds/%ds" "$name" "$elapsed" "$max_wait"
  done
  echo ""
  echo "  [$name] ⚠️  超时未就绪, 请手动检查: $DC logs $name"
  return 1
}

wait_healthy mysql
wait_healthy clickhouse
wait_healthy selenium-chrome
wait_healthy backend

# ----------------------------------------------------------------
# 显示状态
# ----------------------------------------------------------------
echo ""
echo "[4/4] 部署完成 — 服务状态:"
echo ""
$DC ps
echo ""
echo "端口映射 (宿主机 → 容器):"
echo "  MySQL       : 0.0.0.0:3307 → 3306"
echo "  ClickHouse  : 0.0.0.0:8124 → 8123 (HTTP) / 9001 → 9000 (Native)"
echo "  Selenium    : 0.0.0.0:4445 → 4444"
echo "  Backend API : 0.0.0.0:8081 → 8080"
echo ""
echo "运维命令:"
echo "  $DC logs -f backend      # 后端日志"
echo "  $DC logs -f clickhouse   # ClickHouse 日志"
echo "  $DC ps                   # 状态"
echo "  $DC down                 # 停服"
echo "  $DC down -v              # 停服 + 删除数据卷"
echo ""
echo "跑批测试:"
echo "  curl 'http://localhost:8081/api/spider/dc/daily?tradeDate=2026-07-10'"
echo "============================================"
