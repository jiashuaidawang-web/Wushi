# ==================== 第一阶段：编译打包 ====================
FROM node:18 AS web-builder
WORKDIR /web

COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# ==================== 第二阶段：Nginx 运行 ====================
FROM nginx:alpine
# 从前端打包生成的 dist 目录拷贝到 nginx 静态资源目录
COPY --from=web-builder /web/dist/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]