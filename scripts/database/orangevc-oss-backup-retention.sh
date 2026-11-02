#!/usr/bin/env bash
set -euo pipefail
umask 077

dry_run=0
if [[ "${1:-}" == "--dry-run" ]]; then
  dry_run=1
fi

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

delete_expired_prefix() {
  local prefix="$1"
  local retention_days="$2"
  local cutoff_epoch
  local deleted=0
  cutoff_epoch="$(date -d "-${retention_days} days" +%s)"

  while IFS=$'\t' read -r modified_at object_url; do
    [[ -n "$object_url" ]] || continue
    case "$object_url" in
      "oss://${OSS_BACKUP_BUCKET}/${prefix}"*) ;;
      *) echo "Unsafe OSS object outside expected prefix: $object_url" >&2; exit 1 ;;
    esac
    object_epoch="$(date -d "$modified_at" +%s)"
    if (( object_epoch < cutoff_epoch )); then
      if [[ "$dry_run" == "1" ]]; then
        printf 'would_delete=%s\n' "$object_url"
      else
        ossutil rm "$object_url" -f -c "$oss_config" >/dev/null
        printf 'deleted=%s\n' "$object_url"
      fi
      deleted=$((deleted + 1))
    fi
  done < <(ossutil ls "oss://${OSS_BACKUP_BUCKET}/${prefix}" -c "$oss_config" \
    | awk '/^[0-9]{4}-[0-9]{2}-[0-9]{2}/ {print $1 " " $2 "\t" $NF}')

  printf 'prefix=%s retention_days=%s matched=%s dry_run=%s\n' "$prefix" "$retention_days" "$deleted" "$dry_run"
}

delete_expired_prefix 'database-backups/daily/' 30
delete_expired_prefix 'database-backups/checksums/' 30
delete_expired_prefix 'database-backups/monthly/' 400
delete_expired_prefix 'database-backups/rehearsal/' 7
