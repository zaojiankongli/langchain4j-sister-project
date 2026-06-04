# ==================== Stage 1: Build ====================
FROM node:20-alpine AS builder
WORKDIR /app

# 依赖缓存层
COPY package*.json ./
RUN npm ci

# 构建
COPY . .
RUN npm run build

# ==================== Stage 2: Runtime ====================
FROM nginx:stable-alpine
WORKDIR /app

# 构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

# nginx 配置（proxy_pass http://backend:8080 → Docker DNS 解析）
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
