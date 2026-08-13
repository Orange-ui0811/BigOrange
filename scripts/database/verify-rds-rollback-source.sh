#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

cutover_root="/data/orangejd/backups/mysql-cutover"
latest_cutover="$(find "$cutover_root" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -nr | awk 'NR == 1 { print $2 }')"
rds_env="${latest_cutover}/orangejd.env.rds"

test -n "$latest_cutover"
test -r "$rds_env"

# shellcheck disable=SC1090
source "$rds_env"
if [[ "$MYSQL_HOST" == "127.0.0.1" || "$MYSQL_HOST" == "localhost" ]]; then
  echo "The rollback source unexpectedly points at local MySQL" >&2
  exit 1
fi

query() {
  MYSQL_PWD="$MYSQL_PASSWORD" mysql \
    --ssl-mode=REQUIRED \
    --connect-timeout=8 \
    --host="$MYSQL_HOST" \
    --port="$MYSQL_PORT" \
    --user="$MYSQL_USER" \
    --database="$MYSQL_DB" \
    --batch \
    --skip-column-names \
    --execute="$1"
}

test "$(query 'SELECT 1')" = "1"
test "$(query "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()")" = "61"
test "$(query 'SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0')" = "0"
test "$(query 'SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1')" = "37"

echo "rds_rollback_source=reachable tables=61 flyway=37 tls=required"
