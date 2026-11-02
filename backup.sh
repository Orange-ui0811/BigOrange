#!/usr/bin/env bash
# OrangeJD 数据库备份脚本
# 用法: MYSQL_PASSWORD=xxx BACKUP_DIR=/data/backups/orangejd ./backup.sh

set -euo pipefail

: "${MYSQL_PASSWORD:?请配置 MYSQL_PASSWORD}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-orangejd}"
MYSQL_DB="${MYSQL_DB:-orangejd}"
BACKUP_ROOT="${BACKUP_DIR:-./backup}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
STAMP="$(date +%Y-%m-%d_%H%M%S)"
DIR="$BACKUP_ROOT/$(date +%Y-%m-%d)"
TARGET="$DIR/${MYSQL_DB}_$STAMP.sql.gz"
TEMP="$TARGET.tmp"

umask 077
mkdir -p "$DIR"
export MYSQL_PWD="$MYSQL_PASSWORD"
mysqldump --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --single-transaction --quick --routines --triggers --events --set-gtid-purged=OFF \
  "$MYSQL_DB" | gzip -9 > "$TEMP"
gzip -t "$TEMP"
mv "$TEMP" "$TARGET"
unset MYSQL_PWD

find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime "+$RETENTION_DAYS" -exec rm -rf -- {} +
echo "[$(date -Iseconds)] Backup verified: $TARGET"
