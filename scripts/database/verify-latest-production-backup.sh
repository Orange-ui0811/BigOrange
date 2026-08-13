#!/usr/bin/env bash
set -euo pipefail
umask 077

source /etc/orangejd/mysql-backup.env
oss_config="$(mktemp)"
download_dir="$(mktemp -d)"
download_file="$download_dir/production-backup.sql.gz.age"
trap 'rm -f "$oss_config" "$download_file"; rmdir "$download_dir" 2>/dev/null || true' EXIT

cat >"$oss_config" <<EOF
[Credentials]
language = EN
endpoint = ${OSS_BACKUP_ENDPOINT}
accessKeyID = ${OSS_BACKUP_ACCESS_KEY}
accessKeySecret = ${OSS_BACKUP_ACCESS_SECRET}
EOF
chmod 600 "$oss_config"

listing="$(ossutil ls "oss://${OSS_BACKUP_BUCKET}/database-backups/daily/" -c "$oss_config")"
remote_object="$(printf '%s\n' "$listing" | awk '/\.sql\.gz\.age$/ {print $NF}' | tail -n 1)"
test -n "$remote_object"
base_name="$(basename "$remote_object")"
local_file="$(find "$MYSQL_BACKUP_DIR/daily" -type f -name "$base_name" -print -quit)"
test -n "$local_file"

ossutil cp "$remote_object" "$download_file" -c "$oss_config" >/dev/null
cmp "$local_file" "$download_file"
printf 'latest_backup=%s\nsize_bytes=%s\nsha256=%s\n' \
  "$remote_object" \
  "$(stat -c %s "$download_file")" \
  "$(sha256sum "$download_file" | cut -d' ' -f1)"
