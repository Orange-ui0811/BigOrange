#!/usr/bin/env bash
set -euo pipefail
umask 077

source /etc/orangejd/mysql-backup.env
oss_config="$(mktemp)"
work_dir="$(mktemp -d)"
probe_source="$work_dir/probe-source.txt"
probe_download="$work_dir/probe-download.txt"
backup_download="$work_dir/backup-download.sql.gz.age"
stamp="$(date +%Y%m%d-%H%M%S)"
probe_object="oss://${OSS_BACKUP_BUCKET}/database-backups/permission-probe/${stamp}.txt"
backup_object="oss://${OSS_BACKUP_BUCKET}/database-backups/rehearsal/daily/2026/08/orangevc-20260811-124057.sql.gz.age"

cleanup() {
  ossutil rm "$probe_object" -f -c "$oss_config" >/dev/null 2>&1 || true
  rm -f "$oss_config" "$probe_source" "$probe_download" "$backup_download"
  rmdir "$work_dir" 2>/dev/null || true
}
trap cleanup EXIT

cat >"$oss_config" <<EOF
[Credentials]
language = EN
endpoint = ${OSS_BACKUP_ENDPOINT}
accessKeyID = ${OSS_BACKUP_ACCESS_KEY}
accessKeySecret = ${OSS_BACKUP_ACCESS_SECRET}
EOF
chmod 600 "$oss_config"
printf 'orangevc-backup-permission-probe-%s\n' "$stamp" >"$probe_source"

ossutil cp "$probe_source" "$probe_object" -c "$oss_config" >/dev/null
ossutil cp "$probe_object" "$probe_download" -c "$oss_config" >/dev/null
cmp "$probe_source" "$probe_download"

ossutil cp "$backup_object" "$backup_download" -c "$oss_config" >/dev/null
test -s "$backup_download"
expected_hash="$(sha256sum /data/orangejd/backups/mysql/daily/2026/08/orangevc-20260811-124057.sql.gz.age | cut -d' ' -f1)"
download_hash="$(sha256sum "$backup_download" | cut -d' ' -f1)"
test "$expected_hash" = "$download_hash"

ossutil rm "$probe_object" -f -c "$oss_config" >/dev/null
printf 'probe_upload=passed\nprobe_download=passed\nprobe_delete=passed\nbackup_download=passed\nbackup_hash=matched\n'
