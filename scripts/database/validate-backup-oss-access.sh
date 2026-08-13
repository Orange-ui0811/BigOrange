#!/usr/bin/env bash
set -euo pipefail
umask 077

backup_env="/etc/orangejd/mysql-backup.env"
test -r "$backup_env"
# shellcheck disable=SC1090
source "$backup_env"

oss_config="$(mktemp)"
download_dir="$(mktemp -d)"
download_file="$download_dir/rehearsal-backup.sql.gz.age"
trap 'rm -f "$oss_config" "$download_file"; rmdir "$download_dir" 2>/dev/null || true' EXIT
cat >"$oss_config" <<EOF
[Credentials]
language = EN
endpoint = ${OSS_BACKUP_ENDPOINT}
accessKeyID = ${OSS_BACKUP_ACCESS_KEY}
accessKeySecret = ${OSS_BACKUP_ACCESS_SECRET}
EOF
chmod 600 "$oss_config"

backup_prefix="oss://${OSS_BACKUP_BUCKET}/database-backups/"
listing="$(ossutil ls "$backup_prefix" -c "$oss_config")"
remote_object="$(printf '%s\n' "$listing" | awk '/\.sql\.gz\.age$/ {print $NF}' | tail -n 1)"
if [[ -z "$remote_object" ]]; then
  echo "No encrypted rehearsal backup was found in OSS" >&2
  exit 1
fi

ossutil cp "$remote_object" "$download_file" -c "$oss_config" >/dev/null
test -s "$download_file"

base_name="$(basename "$remote_object")"
local_file="$(find "$MYSQL_BACKUP_DIR/daily" -type f -name "$base_name" -print -quit)"
test -n "$local_file"
test "$(sha256sum "$download_file" | cut -d' ' -f1)" = "$(sha256sum "$local_file" | cut -d' ' -f1)"

if ossutil ls "oss://${OSS_BACKUP_BUCKET}/avatar/" -c "$oss_config" >/dev/null 2>&1; then
  echo "Backup credential unexpectedly has access to the avatar prefix" >&2
  exit 1
fi

printf 'backup_prefix=listable\nbackup_object=downloadable\nbackup_hash=matched\nmedia_prefix=denied\n'
