#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="${1:-/home/admin/orangejd-release.tar.gz}"
EXPECTED_SHA="${2:-}"
APP_ROOT="${APP_ROOT:-/data/orangejd}"
SERVICE="${ORANGEVC_SERVICE:-orangejd}"
HEALTH_URL="${ORANGEVC_HEALTH_URL:-http://127.0.0.1:8080/health/detail}"

[[ -f "$ARCHIVE" ]] || { echo "Release archive not found: $ARCHIVE" >&2; exit 1; }

ACTUAL_SHA="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
if [[ -n "$EXPECTED_SHA" && "${ACTUAL_SHA,,}" != "${EXPECTED_SHA,,}" ]]; then
  echo "Release SHA256 mismatch" >&2
  exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
STAGE="$(mktemp -d "/home/admin/orangevc-release-${STAMP}.XXXXXX")"
BACKUP="$APP_ROOT/backups/release-before-$STAMP"
NEW_WEB="$APP_ROOT/web-dist-new-$STAMP"
OLD_WEB="$APP_ROOT/web-dist-old-$STAMP"
ROLLED_BACK=false

cleanup() {
  rm -rf -- "$STAGE"
}

rollback() {
  local exit_code=$?
  if [[ "$ROLLED_BACK" == false && -f "$BACKUP/orange-server.jar" ]]; then
    ROLLED_BACK=true
    echo "Deployment failed; restoring application files from $BACKUP" >&2
    sudo systemctl stop "$SERVICE" || true
    sudo install -o admin -g admin -m 0640 \
      "$BACKUP/orange-server.jar" "$APP_ROOT/orange-server.jar"
    if [[ -d "$BACKUP/web-dist" ]]; then
      sudo rm -rf -- "$APP_ROOT/web-dist"
      sudo cp -a "$BACKUP/web-dist" "$APP_ROOT/web-dist"
      sudo chown -R admin:admin "$APP_ROOT/web-dist"
    fi
    sudo systemctl start "$SERVICE" || true
  fi
  cleanup
  exit "$exit_code"
}

trap rollback ERR
trap cleanup EXIT

tar -xzf "$ARCHIVE" -C "$STAGE"
test -f "$STAGE/orange-server.jar"
test -f "$STAGE/web-dist/index.html"
test -f "$STAGE/web-dist/admin/index.html"
test -f "$STAGE/manifest.json"

sudo install -d -m 0750 "$BACKUP"
sudo cp -a "$APP_ROOT/orange-server.jar" "$BACKUP/"
sudo cp -a "$APP_ROOT/web-dist" "$BACKUP/"
sudo cp -a /etc/orangejd/orangejd.env "$BACKUP/"

sudo install -d -o admin -g admin -m 0755 "$NEW_WEB"
sudo cp -a "$STAGE/web-dist/." "$NEW_WEB/"
sudo chown -R admin:admin "$NEW_WEB"

sudo nginx -t
sudo systemctl stop "$SERVICE"
sudo install -o admin -g admin -m 0640 \
  "$STAGE/orange-server.jar" "$APP_ROOT/orange-server.jar"
sudo mv "$APP_ROOT/web-dist" "$OLD_WEB"
sudo mv "$NEW_WEB" "$APP_ROOT/web-dist"
sudo systemctl start "$SERVICE"

for _ in {1..30}; do
  if curl -fsS "$HEALTH_URL" >/tmp/orangevc-health.json; then
    break
  fi
  sleep 2
done

curl -fsS "$HEALTH_URL"
echo
sudo systemctl is-active --quiet "$SERVICE"
sudo systemctl reload nginx

trap - ERR
echo "Deployment succeeded"
echo "Backup: $BACKUP"
echo "Archive SHA256: $ACTUAL_SHA"
