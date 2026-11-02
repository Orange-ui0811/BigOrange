#!/usr/bin/env bash
set -euo pipefail
umask 077

kill 267225 267208 267207 2>/dev/null || true
sleep 1

source /etc/orangejd/mysql-backup.env
oss_config="$(mktemp)"
trap 'rm -f "$oss_config"' EXIT
cat >"$oss_config" <<EOF
[Credentials]
language = EN
endpoint = ${OSS_BACKUP_ENDPOINT}
accessKeyID = ${OSS_BACKUP_ACCESS_KEY}
accessKeySecret = ${OSS_BACKUP_ACCESS_SECRET}
EOF
chmod 600 "$oss_config"
ossutil rm "oss://${OSS_BACKUP_BUCKET}/database-backups/permission-probe/20260811-160732.txt" -f -c "$oss_config"

if ps -p 267225,267208,267207 >/dev/null 2>&1; then
  echo "Probe processes are still running" >&2
  exit 1
fi
printf 'probe_processes=stopped\nprobe_object=deleted\n'
