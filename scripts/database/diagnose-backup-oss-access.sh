#!/usr/bin/env bash
set -u
umask 077

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

echo 'stage=list-backup-prefix'
ossutil ls "oss://${OSS_BACKUP_BUCKET}/database-backups/" -c "$oss_config"
echo "list_exit=$?"

echo 'stage=stat-backup-object'
ossutil stat "oss://${OSS_BACKUP_BUCKET}/database-backups/rehearsal/daily/2026/08/orangevc-20260811-124057.sql.gz.age" -c "$oss_config"
echo "stat_exit=$?"

echo 'stage=list-media-prefix-expected-denied'
ossutil ls "oss://${OSS_BACKUP_BUCKET}/avatar/" -c "$oss_config"
echo "media_list_exit=$?"
