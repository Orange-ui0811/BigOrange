#!/bin/bash
# OrangeJD 数据库备份脚本
# 用法: MYSQL_PASSWORD=xxx ./backup.sh
# crontab: 0 3 * * * cd /data/scripts && MYSQL_PASSWORD=xxx ./backup.sh

DIR="backup/$(date +%Y-%m-%d)"
mkdir -p "$DIR"
mysqldump -u orangejd_user -p"$MYSQL_PASSWORD" --single-transaction orangejd | gzip > "$DIR/orangejd_$(date +%H%M).sql.gz"
echo "[$(date)] Backup saved to $DIR/orangejd_$(date +%H%M).sql.gz"

# 保留最近7天
find backup/ -maxdepth 1 -type d -mtime +7 -exec rm -rf {} \; 2>/dev/null
