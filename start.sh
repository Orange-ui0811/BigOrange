#!/bin/bash
# OrangeJD 服务器启动脚本
# 部署路径: /root/start.sh
# 启动方式: nohup /root/start.sh > /root/app.log 2>&1 &

: "${MYSQL_PASSWORD:?请配置 MYSQL_PASSWORD}"
: "${REDIS_PASSWORD:?请配置 REDIS_PASSWORD}"
: "${JWT_ADMIN_SECRET:?请配置 JWT_ADMIN_SECRET}"
: "${JWT_APP_SECRET:?请配置 JWT_APP_SECRET}"
: "${ALLOWED_ORIGINS:?请配置 ALLOWED_ORIGINS}"
: "${TURNSTILE_SECRET_KEY:?请配置 TURNSTILE_SECRET_KEY}"

export MYSQL_USER="${MYSQL_USER:-orangejd}"
export MYSQL_DB="${MYSQL_DB:-orangejd}"
export UPLOAD_PATH="${UPLOAD_PATH:-/data/orangejd/uploads}"

exec "${JAVA_BIN:-java}" -jar "${APP_JAR:-/root/orange-server-1.0.0-SNAPSHOT.jar}" --spring.profiles.active=prod
