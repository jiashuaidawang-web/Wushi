# ============================================
# Wushi Backend Dockerfile with Playwright+Chromium
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app
COPY pom.xml .
COPY wushi-api/pom.xml wushi-api/
COPY wushi-common/pom.xml wushi-common/
COPY wushi-infrastructure/pom.xml wushi-infrastructure/
COPY wushi-modules/pom.xml wushi-modules/
COPY wushi-modules/module-spider/pom.xml wushi-modules/module-spider/
COPY wushi-modules/module-market/pom.xml wushi-modules/module-market/
COPY wushi-modules/module-emotion/pom.xml wushi-modules/module-emotion/
COPY wushi-modules/module-mainline/pom.xml wushi-modules/module-mainline/
COPY wushi-modules/module-leader/pom.xml wushi-modules/module-leader/
COPY wushi-modules/module-pattern/pom.xml wushi-modules/module-pattern/
COPY wushi-modules/module-risk/pom.xml wushi-modules/module-risk/
COPY wushi-modules/module-similarity/pom.xml wushi-modules/module-similarity/
COPY wushi-modules/module-review/pom.xml wushi-modules/module-review/
COPY wushi-modules/module-backtest/pom.xml wushi-modules/module-backtest/
COPY wushi-modules/module-rule/pom.xml wushi-modules/module-rule/
COPY wushi-modules/module-agent-audit/pom.xml wushi-modules/module-agent-audit/
COPY wushi-app/pom.xml wushi-app/
#COPY wushi-web/pom.xml wushi-web/

# 下载依赖（如果存在）
RUN mvn dependency:go-offline -B || true

# 复制源码并构建
COPY . .
RUN mvn clean package -DskipTests -B

# ============================================
# Runtime Image - 带 Chromium 的完整系统
# ============================================
FROM eclipse-temurin:21-jre-jammy

# 安装 Chromium + Playwright 所有运行时依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libx11-xcb1 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxkbcommon0 \
    libxrandr2 \
    xdg-utils \
    && wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - \
    && echo "deb http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
    google-chrome-stable \
    || (apt-get install -y --no-install-recommends chromium-browser) \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户 (Chromium 不能用 root 跑)
RUN groupadd -r wushi && useradd -r -g wushi -G audio,video wushi \
    && mkdir -p /home/wushi /app \
    && chown -R wushi:wushi /home/wushi /app

WORKDIR /app

# 从 builder 复制 JAR
COPY --from=builder --chown=wushi:wushi /app/wushi-app/target/*.jar app.jar

# Playwright 配置: 使用系统 Chromium，不下载自带版本
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/google-chrome-stable

USER wushi

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
