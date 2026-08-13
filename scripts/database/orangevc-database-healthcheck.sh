#!/usr/bin/env bash
set -euo pipefail

issues=()
for service_name in mysql.service orangejd.service nginx.service orangevc-mysql-backup.timer; do
  if ! systemctl is-active --quiet "$service_name"; then
    issues+=("${service_name} is not active")
  fi
done

if ! mysql -N -e 'SELECT 1' >/dev/null 2>&1; then
  issues+=("local MySQL query failed")
fi

application_health_ok=false
for attempt in 1 2 3; do
  if curl -fsS --max-time 10 http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
    application_health_ok=true
    break
  fi
  if (( attempt < 3 )); then
    sleep 2
  fi
done
if [[ "$application_health_ok" != true ]]; then
  issues+=("application health endpoint failed")
fi

if ! grep -Fxq 'MYSQL_HOST=127.0.0.1' /etc/orangejd/orangejd.env; then
  issues+=("application is not configured for local MySQL")
fi

disk_percent="$(df --output=pcent / | tail -n 1 | tr -dc '0-9')"
if (( disk_percent >= 85 )); then
  issues+=("root disk usage is ${disk_percent}%")
elif (( disk_percent >= 70 )); then
  logger -p daemon.warning -t orangevc-database-health "root disk warning: ${disk_percent}%"
fi

latest_backup_epoch="$(find /data/orangejd/backups/mysql/daily -type f -name '*.sql.gz.age' -printf '%T@\n' 2>/dev/null | sort -nr | head -n 1 | cut -d. -f1)"
if [[ -z "$latest_backup_epoch" ]]; then
  issues+=("no local encrypted database backup exists")
else
  backup_age_seconds=$(( $(date +%s) - latest_backup_epoch ))
  if (( backup_age_seconds > 93600 )); then
    issues+=("latest encrypted database backup is older than 26 hours")
  fi
fi

if (( ${#issues[@]} > 0 )); then
  message="OrangeVC database health check failed on $(hostname): $(IFS='; '; echo "${issues[*]}")"
  logger -p daemon.err -t orangevc-database-health "$message"
  if grep -q '^FEISHU_WEBHOOK_URL=.' /etc/orangejd/orangejd.env; then
    source /etc/orangejd/orangejd.env
    payload="$(python3 -c 'import json,sys; print(json.dumps({"msg_type":"text","content":{"text":sys.argv[1]}}))' "$message")"
    curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "$payload" "$FEISHU_WEBHOOK_URL" >/dev/null 2>&1 || true
  fi
  printf '%s\n' "${issues[@]}" >&2
  exit 1
fi

printf 'database_health=ok disk_percent=%s backup_age_seconds=%s\n' "$disk_percent" "$backup_age_seconds"
