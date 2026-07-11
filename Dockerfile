# ==================== 第一阶段：编译打包 ====================
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

# 将后端代码复制到容器中
COPY . .

# 执行 Maven 打包（跳过测试）
RUN mvn clean package -DskipTests

# ==================== 第二阶段：运行镜像 ====================
FROM openjdk:21-slim
WORKDIR /app

# 从编译阶段复制生成的 jar 包
COPY --from=builder /build/target/*.jar app.jar

# 暴露后端端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]