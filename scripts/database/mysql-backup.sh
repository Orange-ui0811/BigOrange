#!/usr/bin/env bash
set -euo pipefail
umask 077

backup_env="/etc/orangejd/mysql-backup.env"
test -r "$backup_env"
# shellcheck disable=SC1090
source "$backup_env"

: "${MYSQL_BACKUP_HOST:?}"
: "${MYSQL_BACKUP_PORT:?}"
: "${MYSQL_BACKUP_DB:?}"
: "${MYSQL_BACKUP_USER:?}"
: "${MYSQL_BACKUP_PASSWORD:?}"
: "${OSS_BACKUP_ENDPOINT:?}"
: "${OSS_BACKUP_BUCKET:?}"
: "${OSS_BACKUP_ACCESS_KEY:?}"
: "${OSS_BACKUP_ACCESS_SECRET:?}"
: "${AGE_RECIPIENT_FILE:?}"
: "${MYSQL_BACKUP_DIR:?}"

backup_database="${MYSQL_BACKUP_DB_OVERRIDE:-$MYSQL_BACKUP_DB}"
backup_prefix="${OSS_BACKUP_PREFIX_OVERRIDE:-database-backups}"

resolved_backup_dir="$(readlink -m "$MYSQL_BACKUP_DIR")"
case "$resolved_backup_dir" in
  /data/orangejd/backups/mysql|/data/orangejd/backups/mysql/*) ;;
  *) echo "Unsafe backup directory: $resolved_backup_dir" >&2; exit 1 ;;
esac

stamp="$(date +%Y%m%d-%H%M%S)"
year="$(date +%Y)"
month="$(date +%m)"
day="$(date +%d)"
daily_dir="$resolved_backup_dir/daily/$year/$month"
base_name="orangevc-$stamp.sql.gz"
raw_file="$daily_dir/$base_name"
encrypted_file="$raw_file.age"
checksum_file="$encrypted_file.sha256"
oss_config="$(mktemp)"
verify_dir="$(mktemp -d)"
verify_backup="$verify_dir/backup.sql.gz.age"
verify_checksum="$verify_dir/backup.sha256"
trap 'rm -f "$raw_file" "$oss_config" "$verify_backup" "$verify_checksum"; rmdir "$verify_dir" 2>/dev/null || true' EXIT

install -d -o root -g root -m 700 "$daily_dir"
cat >"$oss_config" <<EOF
[Credentials]
language = EN
endpoint = ${OSS_BACKUP_ENDPOINT}
accessKeyID = ${OSS_BACKUP_ACCESS_KEY}
accessKeySecret = ${OSS_BACKUP_ACCESS_SECRET}
EOF
chmod 600 "$oss_config"

MYSQL_PWD="$MYSQL_BACKUP_PASSWORD" mysqldump \
  --ssl-mode=REQUIRED \
  --host="$MYSQL_BACKUP_HOST" \
  --port="$MYSQL_BACKUP_PORT" \
  --user="$MYSQL_BACKUP_USER" \
  --single-transaction \
  --quick \
  --skip-lock-tables \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb3 \
  "$backup_database" | gzip -9 >"$raw_file"

test -s "$raw_file"
age -R "$AGE_RECIPIENT_FILE" -o "$encrypted_file" "$raw_file"
test -s "$encrypted_file"
sha256sum "$encrypted_file" >"$checksum_file"
rm -f "$raw_file"

daily_object="oss://${OSS_BACKUP_BUCKET}/${backup_prefix}/daily/${year}/${month}/$(basename "$encrypted_file")"
checksum_object="oss://${OSS_BACKUP_BUCKET}/${backup_prefix}/checksums/${year}/${month}/$(basename "$checksum_file")"
ossutil cp "$encrypted_file" "$daily_object" -c "$oss_config"
ossutil cp "$checksum_file" "$checksum_object" -c "$oss_config"
ossutil cp "$daily_object" "$verify_backup" -c "$oss_config" >/dev/null
ossutil cp "$checksum_object" "$verify_checksum" -c "$oss_config" >/dev/null
cmp "$encrypted_file" "$verify_backup"
cmp "$checksum_file" "$verify_checksum"

if [[ "$day" == "01" && "$backup_prefix" == "database-backups" ]]; then
  monthly_object="oss://${OSS_BACKUP_BUCKET}/database-backups/monthly/${year}/orangevc-${year}${month}.sql.gz.age"
  ossutil cp "$encrypted_file" "$monthly_object" -c "$oss_config"
fi

find "$resolved_backup_dir/daily" -type f -mtime +7 -delete
printf 'backup=%s\nobject=%s\nchecksum=%s\n' "$encrypted_file" "$daily_object" "$checksum_object"
