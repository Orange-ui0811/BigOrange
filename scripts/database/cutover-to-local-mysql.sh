#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

production_env="/etc/orangejd/orangejd.env"
candidate_env="/etc/orangejd/mysql-selfhost.env"
recipient_file="/etc/orangejd/mysql-backup-recipient"
stamp="$(date +%Y%m%d-%H%M%S)"
cutover_dir="/data/orangejd/backups/mysql-cutover/$stamp"
dump_file="$cutover_dir/rds-final-$stamp.sql.gz"
encrypted_dump="$dump_file.age"
old_env="$cutover_dir/orangejd.env.rds"
new_env_tmp="$(mktemp)"
app_stopped=0
cutover_complete=0

install -d -o root -g root -m 700 "$cutover_dir"
trap 'rm -f "$new_env_tmp"' EXIT

rollback_on_error() {
  local exit_code=$?
  if [[ "$cutover_complete" == "1" ]]; then
    exit "$exit_code"
  fi
  echo "Cutover failed; restoring the RDS environment before reopening the site." >&2
  systemctl stop orangejd.service >/dev/null 2>&1 || true
  if [[ -s "$old_env" ]]; then
    install -o root -g root -m 600 "$old_env" "$production_env"
  fi
  systemctl start orangejd.service >/dev/null 2>&1 || true
  systemctl start nginx.service >/dev/null 2>&1 || true
  exit "$exit_code"
}
trap rollback_on_error ERR

test -r "$production_env"
test -r "$candidate_env"
test -s "$recipient_file"
systemctl is-active --quiet orangejd.service
systemctl is-active --quiet nginx.service
systemctl is-active --quiet mysql.service

# Capture the immutable RDS source settings before loading the local candidate.
# shellcheck disable=SC1090
source "$production_env"
rds_host="$MYSQL_HOST"
rds_port="$MYSQL_PORT"
rds_db="$MYSQL_DB"
rds_user="$MYSQL_USER"
rds_password="$MYSQL_PASSWORD"
if [[ "$rds_host" == "127.0.0.1" || "$rds_host" == "localhost" ]]; then
  echo "Production is already configured for local MySQL; refusing duplicate cutover." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$candidate_env"
local_host="$MYSQL_HOST"
local_port="$MYSQL_PORT"
local_db="$MYSQL_DB"
local_user="$MYSQL_USER"
local_password="$MYSQL_PASSWORD"
test "$local_host" = "127.0.0.1"
test "$local_db" = "orangevc"

install -o root -g root -m 600 "$production_env" "$old_env"
tar -C /etc -czf "$cutover_dir/nginx-before-$stamp.tar.gz" nginx
sha256sum /data/orangejd/orange-server.jar >"$cutover_dir/orange-server.jar.sha256"

# Keep the public site closed until local validation and encrypted backup succeed.
systemctl stop nginx.service
systemctl stop orangejd.service
app_stopped=1
if systemctl is-active --quiet orangejd.service; then
  echo "Application did not stop" >&2
  exit 1
fi

MYSQL_PWD="$rds_password" mysqldump \
  --ssl-mode=REQUIRED \
  --host="$rds_host" \
  --port="$rds_port" \
  --user="$rds_user" \
  --single-transaction \
  --quick \
  --skip-lock-tables \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb3 \
  "$rds_db" | gzip -9 >"$dump_file"
test -s "$dump_file"

source_counts="$cutover_dir/rds-table-counts.tsv"
target_counts="$cutover_dir/local-table-counts.tsv"
while IFS= read -r table_name; do
  MYSQL_PWD="$rds_password" mysql --ssl-mode=REQUIRED \
    --host="$rds_host" --port="$rds_port" --user="$rds_user" \
    --database="$rds_db" --batch --skip-column-names \
    --execute="SELECT CONCAT('${table_name}', CHAR(9), COUNT(*)) FROM \`${table_name}\`" >>"$source_counts"
done < <(MYSQL_PWD="$rds_password" mysql --ssl-mode=REQUIRED \
  --host="$rds_host" --port="$rds_port" --user="$rds_user" \
  --database="$rds_db" --batch --skip-column-names --execute='SHOW TABLES')

local_existing_tables="$(mysql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='orangevc'")"
if [[ "$local_existing_tables" != "0" ]]; then
  if [[ "$local_existing_tables" != "61" ]]; then
    echo "Local production database has an unexpected table count; refusing replacement." >&2
    exit 1
  fi
  test "$(mysql -N orangevc -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")" = "0"
  test "$(mysql -N orangevc -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1")" = "37"
fi
mysql -e "DROP DATABASE IF EXISTS orangevc; CREATE DATABASE orangevc CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci; GRANT ALL PRIVILEGES ON orangevc.* TO 'orangevc_app'@'127.0.0.1'; GRANT SELECT, SHOW VIEW, TRIGGER, EVENT ON orangevc.* TO 'orangevc_backup'@'127.0.0.1';"
gzip -dc "$dump_file" | mysql --default-character-set=utf8mb3 orangevc

while IFS= read -r table_name; do
  mysql --database=orangevc --batch --skip-column-names \
    --execute="SELECT CONCAT('${table_name}', CHAR(9), COUNT(*)) FROM \`${table_name}\`" >>"$target_counts"
done < <(mysql --database=orangevc --batch --skip-column-names --execute='SHOW TABLES')
sort -o "$source_counts" "$source_counts"
sort -o "$target_counts" "$target_counts"
diff -u "$source_counts" "$target_counts" >"$cutover_dir/table-counts.diff"
test ! -s "$cutover_dir/table-counts.diff"
test "$(mysql -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='orangevc'")" = "61"
test "$(mysql -N orangevc -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")" = "0"
test "$(mysql -N orangevc -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1")" = "37"

age -R "$recipient_file" -o "$encrypted_dump" "$dump_file"
test -s "$encrypted_dump"
sha256sum "$encrypted_dump" >"$encrypted_dump.sha256"
rm -f "$dump_file"

awk '!/^MYSQL_(HOST|PORT|DB|USER|PASSWORD)=/' "$old_env" >"$new_env_tmp"
printf '%s\n' \
  "MYSQL_HOST=${local_host}" \
  "MYSQL_PORT=${local_port}" \
  "MYSQL_DB=${local_db}" \
  "MYSQL_USER=${local_user}" \
  "MYSQL_PASSWORD=${local_password}" >>"$new_env_tmp"
install -o root -g root -m 600 "$new_env_tmp" "$production_env"

systemctl start orangejd.service
for _ in $(seq 1 45); do
  if curl -fsS http://127.0.0.1:8081/actuator/health >"$cutover_dir/health.json"; then
    break
  fi
  sleep 2
done
curl -fsS http://127.0.0.1:8081/actuator/health >/dev/null
systemctl is-active --quiet orangejd.service

ssl_cipher="$(mysql -N -e "SELECT VARIABLE_VALUE FROM performance_schema.status_by_thread s JOIN performance_schema.threads t ON t.THREAD_ID=s.THREAD_ID WHERE s.VARIABLE_NAME='Ssl_cipher' AND t.PROCESSLIST_USER='orangevc_app' AND s.VARIABLE_VALUE<>'' LIMIT 1")"
test -n "$ssl_cipher"

systemctl start orangevc-mysql-backup.service
systemctl is-failed --quiet orangevc-mysql-backup.service && exit 1 || true
systemctl enable --now orangevc-mysql-backup.timer
systemctl is-active --quiet orangevc-mysql-backup.timer

cutover_complete=1
trap - ERR
systemctl start nginx.service
systemctl is-active --quiet nginx.service
curl -fsS https://orangevf.com/ >/dev/null

printf 'cutover=complete\ncutover_dir=%s\ntables=61\nflyway=37\nssl_cipher=%s\n' "$cutover_dir" "$ssl_cipher"
