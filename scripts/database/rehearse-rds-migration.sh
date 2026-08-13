#!/usr/bin/env bash
set -euo pipefail
umask 077

source_env="/etc/orangejd/orangejd.env"
test -r "$source_env"
# shellcheck disable=SC1090
source "$source_env"

: "${MYSQL_HOST:?}"
: "${MYSQL_PORT:?}"
: "${MYSQL_DB:?}"
: "${MYSQL_USER:?}"
: "${MYSQL_PASSWORD:?}"

stamp="$(date +%Y%m%d-%H%M%S)"
migration_dir="/data/orangejd/backups/database-migration"
dump_file="$migration_dir/rehearsal-$stamp.sql.gz"
install -d -o root -g root -m 700 "$migration_dir"

MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  --ssl-mode=REQUIRED \
  --host="$MYSQL_HOST" \
  --port="$MYSQL_PORT" \
  --user="$MYSQL_USER" \
  --single-transaction \
  --quick \
  --skip-lock-tables \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb3 \
  "$MYSQL_DB" | gzip -9 >"$dump_file"

test -s "$dump_file"
sha256sum "$dump_file" >"$dump_file.sha256"

mysql -e "DROP DATABASE IF EXISTS orangevc_rehearsal; CREATE DATABASE orangevc_rehearsal CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci; GRANT ALL PRIVILEGES ON orangevc_rehearsal.* TO 'orangevc_app'@'127.0.0.1';"
gzip -dc "$dump_file" | mysql --default-character-set=utf8mb3 orangevc_rehearsal

source_counts="$(mktemp)"
target_counts="$(mktemp)"
trap 'rm -f "$source_counts" "$target_counts"' EXIT

while IFS= read -r table_name; do
  MYSQL_PWD="$MYSQL_PASSWORD" mysql --ssl-mode=REQUIRED \
    --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
    --database="$MYSQL_DB" --batch --skip-column-names \
    --execute="SELECT CONCAT('${table_name}', CHAR(9), COUNT(*)) FROM \`${table_name}\`" >>"$source_counts"
  mysql --database=orangevc_rehearsal --batch --skip-column-names \
    --execute="SELECT CONCAT('${table_name}', CHAR(9), COUNT(*)) FROM \`${table_name}\`" >>"$target_counts"
done < <(MYSQL_PWD="$MYSQL_PASSWORD" mysql --ssl-mode=REQUIRED \
  --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
  --database="$MYSQL_DB" --batch --skip-column-names --execute='SHOW TABLES')

sort -o "$source_counts" "$source_counts"
sort -o "$target_counts" "$target_counts"
diff -u "$source_counts" "$target_counts"

table_count="$(mysql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='orangevc_rehearsal'")"
flyway_failed="$(mysql -N orangevc_rehearsal -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")"
printf 'dump_file=%s\ntable_count=%s\nflyway_failed=%s\n' "$dump_file" "$table_count" "$flyway_failed"
