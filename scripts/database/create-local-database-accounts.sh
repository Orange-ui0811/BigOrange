#!/usr/bin/env bash
set -euo pipefail
umask 077

production_env="/etc/orangejd/orangejd.env"
candidate_env="/etc/orangejd/mysql-selfhost.env"
backup_env="/etc/orangejd/mysql-backup.env"

test -r "$production_env"
test -s /etc/orangejd/mysql-backup-recipient

# shellcheck disable=SC1090
source "$production_env"
: "${OSS_ENDPOINT:?OSS_ENDPOINT is required}"
: "${OSS_BUCKET:?OSS_BUCKET is required}"
: "${OSS_ACCESS_KEY:?OSS_ACCESS_KEY is required}"
: "${OSS_ACCESS_SECRET:?OSS_ACCESS_SECRET is required}"

app_password="$(openssl rand -hex 32)"
backup_password="$(openssl rand -hex 32)"
sql_file="$(mktemp)"
trap 'rm -f "$sql_file"' EXIT

cat >"$sql_file" <<SQL
CREATE DATABASE IF NOT EXISTS orangevc CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci;
CREATE DATABASE IF NOT EXISTS orangevc_rehearsal CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci;
CREATE USER IF NOT EXISTS 'orangevc_app'@'127.0.0.1' IDENTIFIED BY '${app_password}';
ALTER USER 'orangevc_app'@'127.0.0.1' IDENTIFIED BY '${app_password}';
CREATE USER IF NOT EXISTS 'orangevc_backup'@'127.0.0.1' IDENTIFIED BY '${backup_password}';
ALTER USER 'orangevc_backup'@'127.0.0.1' IDENTIFIED BY '${backup_password}';
GRANT ALL PRIVILEGES ON orangevc.* TO 'orangevc_app'@'127.0.0.1';
GRANT ALL PRIVILEGES ON orangevc_rehearsal.* TO 'orangevc_app'@'127.0.0.1';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON orangevc.* TO 'orangevc_backup'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
mysql <"$sql_file"

candidate_tmp="$(mktemp)"
backup_tmp="$(mktemp)"
trap 'rm -f "$sql_file" "$candidate_tmp" "$backup_tmp"' EXIT

printf '%s\n' \
  'MYSQL_HOST=127.0.0.1' \
  'MYSQL_PORT=3306' \
  'MYSQL_DB=orangevc' \
  'MYSQL_USER=orangevc_app' \
  "MYSQL_PASSWORD=${app_password}" >"$candidate_tmp"

printf '%s\n' \
  'MYSQL_BACKUP_HOST=127.0.0.1' \
  'MYSQL_BACKUP_PORT=3306' \
  'MYSQL_BACKUP_DB=orangevc' \
  'MYSQL_BACKUP_USER=orangevc_backup' \
  "MYSQL_BACKUP_PASSWORD=${backup_password}" \
  "OSS_BACKUP_ENDPOINT=${OSS_ENDPOINT}" \
  "OSS_BACKUP_BUCKET=${OSS_BUCKET}" \
  "OSS_BACKUP_ACCESS_KEY=${OSS_ACCESS_KEY}" \
  "OSS_BACKUP_ACCESS_SECRET=${OSS_ACCESS_SECRET}" \
  'AGE_RECIPIENT_FILE=/etc/orangejd/mysql-backup-recipient' \
  'MYSQL_BACKUP_DIR=/data/orangejd/backups/mysql' >"$backup_tmp"

install -o root -g root -m 600 "$candidate_tmp" "$candidate_env"
install -o root -g root -m 600 "$backup_tmp" "$backup_env"
install -d -o root -g root -m 700 /data/orangejd/backups/mysql

mysql -N -e "SELECT SCHEMA_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('orangevc','orangevc_rehearsal') ORDER BY SCHEMA_NAME"
