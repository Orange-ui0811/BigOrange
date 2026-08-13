#!/usr/bin/env bash
set -euo pipefail
umask 077

source_file="${1:?source env file is required}"
backup_env="/etc/orangejd/mysql-backup.env"
test -r "$source_file"
test -r "$backup_env"

read_env_value() {
  local name="$1"
  local value
  value="$(grep -E "^${name}=" "$source_file" | tail -n 1 | cut -d= -f2- | tr -d '\r')"
  value="${value#\"}"
  value="${value%\"}"
  value="${value#\'}"
  value="${value%\'}"
  printf '%s' "$value"
}

new_access_key="$(read_env_value OSS_BACKUP_ACCESS_KEY)"
new_access_secret="$(read_env_value OSS_BACKUP_ACCESS_SECRET)"
if [[ ${#new_access_key} -lt 12 || ${#new_access_secret} -lt 20 ]]; then
  echo "OSS backup credentials are missing or too short" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$backup_env"
tmp_env="$(mktemp)"
trap 'rm -f "$tmp_env"; shred -u "$source_file" 2>/dev/null || true' EXIT

printf '%s\n' \
  "MYSQL_BACKUP_HOST=${MYSQL_BACKUP_HOST}" \
  "MYSQL_BACKUP_PORT=${MYSQL_BACKUP_PORT}" \
  "MYSQL_BACKUP_DB=${MYSQL_BACKUP_DB}" \
  "MYSQL_BACKUP_USER=${MYSQL_BACKUP_USER}" \
  "MYSQL_BACKUP_PASSWORD=${MYSQL_BACKUP_PASSWORD}" \
  "OSS_BACKUP_ENDPOINT=${OSS_BACKUP_ENDPOINT}" \
  "OSS_BACKUP_BUCKET=${OSS_BACKUP_BUCKET}" \
  "OSS_BACKUP_ACCESS_KEY=${new_access_key}" \
  "OSS_BACKUP_ACCESS_SECRET=${new_access_secret}" \
  "AGE_RECIPIENT_FILE=${AGE_RECIPIENT_FILE}" \
  "MYSQL_BACKUP_DIR=${MYSQL_BACKUP_DIR}" >"$tmp_env"

install -o root -g root -m 600 "$tmp_env" "$backup_env"
printf 'backup_credentials=updated\n'
