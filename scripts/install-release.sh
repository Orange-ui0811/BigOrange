#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="${1:-/home/admin/orangejd-release.tar.gz}"
EXPECTED_SHA="${2:-}"
APP_ROOT="${APP_ROOT:-/data/orangejd}"
SERVICE="${ORANGEVC_SERVICE:-orangejd}"
HEALTH_URL="${ORANGEVC_HEALTH_URL:-http://127.0.0.1:8080/health/detail}"
PRODUCTION_ENV="${ORANGEVC_ENV_FILE:-/etc/orangejd/orangejd.env}"

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
NGINX_CHANGED=false
NGINX_HAD_CONFIG=false
ROLLED_BACK=false

cleanup() {
  rm -rf -- "$STAGE"
}

restore_nginx() {
  if [[ "$NGINX_HAD_CONFIG" == true ]] && sudo test -f "$BACKUP/nginx-site.conf"; then
    sudo install -o root -g root -m 0644 "$BACKUP/nginx-site.conf" "$NGINX_TARGET"
  else
    sudo rm -f -- "$NGINX_TARGET"
  fi
  sudo nginx -t && sudo systemctl reload nginx || true
}

rollback() {
  local exit_code=$?
  set +e
  if [[ "$ROLLED_BACK" == false ]]; then
    ROLLED_BACK=true
    echo "Deployment failed; restoring files from $BACKUP" >&2
    if sudo test -f "$BACKUP/orange-server.jar"; then
      sudo systemctl stop "$SERVICE" || true
      sudo install -o admin -g admin -m 0640 "$BACKUP/orange-server.jar" "$APP_ROOT/orange-server.jar"
      if sudo test -d "$BACKUP/web-dist"; then
        sudo rm -rf -- "$APP_ROOT/web-dist"
        sudo cp -a "$BACKUP/web-dist" "$APP_ROOT/web-dist"
        sudo chown -R admin:admin "$APP_ROOT/web-dist"
      fi
      sudo systemctl start "$SERVICE" || true
    fi
    if [[ "$NGINX_CHANGED" == true ]]; then restore_nginx; fi
  fi
  cleanup
  exit "$exit_code"
}

assert_http_status() {
  local url="$1" expected="$2" actual
  actual="$(curl "${CURL_TARGET_ARGS[@]}" --max-time 15 -sS -o /dev/null -w '%{http_code}' "$url")"
  [[ "$actual" == "$expected" ]] || { echo "$url returned $actual, expected $expected" >&2; return 1; }
}

assert_content_type() {
  local url="$1" expected="$2"
  curl "${CURL_TARGET_ARGS[@]}" --max-time 15 -fsSI "$url" | tr -d '\r' | grep -Eiq "^Content-Type: ${expected}"
}

trap rollback ERR
trap cleanup EXIT

tar -xzf "$ARCHIVE" -C "$STAGE"
test -f "$STAGE/orange-server.jar"
test -f "$STAGE/web-dist/index.html"
test -f "$STAGE/web-dist/admin/index.html"
test -f "$STAGE/manifest.json"
test -f "$STAGE/config/public-build-config.sha256"
test -f "$STAGE/config/production-config.sh"
test -f "$STAGE/config/nginx.conf.example"

# Copy the root-only environment into the private stage solely for validation.
sudo cat "$PRODUCTION_ENV" > "$STAGE/production.env"
chmod 0600 "$STAGE/production.env"
source "$STAGE/config/production-config.sh"
load_public_build_config_from_env_file "$STAGE/production.env"
validate_public_build_config
BUILT_PUBLIC_SHA="$(tr -d '[:space:]' < "$STAGE/config/public-build-config.sha256")"
PRODUCTION_PUBLIC_SHA="$(public_build_config_fingerprint)"
[[ "$BUILT_PUBLIC_SHA" == "$PRODUCTION_PUBLIC_SHA" ]] || {
  echo "Release public build configuration does not match production" >&2
  exit 1
}

DOMAIN="${VITE_OFFICIAL_SITE_URL#https://}"
[[ "$DOMAIN" == "orangevf.com" ]] || { echo "Unexpected production domain: $DOMAIN" >&2; exit 1; }
NGINX_CERT="$(read_env_file_value "$STAGE/production.env" NGINX_SSL_CERTIFICATE "/etc/letsencrypt/live/$DOMAIN/fullchain.pem")"
NGINX_CERT_KEY="$(read_env_file_value "$STAGE/production.env" NGINX_SSL_CERTIFICATE_KEY "/etc/letsencrypt/live/$DOMAIN/privkey.pem")"
NGINX_CONFIG_INPUT="$(read_env_file_value "$STAGE/production.env" NGINX_SITE_CONFIG "/etc/nginx/sites-enabled/orangejd")"
[[ "$NGINX_CERT" =~ ^/[A-Za-z0-9._/-]+$ && "$NGINX_CERT_KEY" =~ ^/[A-Za-z0-9._/-]+$ ]] || { echo "Unsafe Nginx certificate path" >&2; exit 1; }
[[ "$NGINX_CONFIG_INPUT" =~ ^/etc/nginx/[A-Za-z0-9._/-]+$ ]] || { echo "Unsafe Nginx site config path" >&2; exit 1; }
sudo test -f "$NGINX_CERT"
sudo test -f "$NGINX_CERT_KEY"
if sudo test -e "$NGINX_CONFIG_INPUT"; then
  NGINX_TARGET="$(sudo readlink -f "$NGINX_CONFIG_INPUT")"
else
  NGINX_TARGET="$NGINX_CONFIG_INPUT"
fi

sed \
  -e "s|@@ORANGEVC_DOMAIN@@|$DOMAIN|g" \
  -e "s|@@SSL_CERTIFICATE@@|$NGINX_CERT|g" \
  -e "s|@@SSL_CERTIFICATE_KEY@@|$NGINX_CERT_KEY|g" \
  "$STAGE/config/nginx.conf.example" > "$STAGE/nginx.conf.rendered"
if grep -Eq '@@[A-Z0-9_]+@@|YOUR_|example\.com' "$STAGE/nginx.conf.rendered"; then
  echo "Rendered Nginx config contains a placeholder or local address" >&2
  exit 1
fi

sudo install -d -m 0750 "$BACKUP"
sudo cp -a "$APP_ROOT/orange-server.jar" "$BACKUP/"
sudo cp -a "$APP_ROOT/web-dist" "$BACKUP/"
sudo cp -a "$PRODUCTION_ENV" "$BACKUP/orangejd.env"
if sudo test -e "$NGINX_TARGET"; then
  NGINX_HAD_CONFIG=true
  sudo cp -a "$NGINX_TARGET" "$BACKUP/nginx-site.conf"
fi

sudo install -d -o admin -g admin -m 0755 "$NEW_WEB"
sudo cp -a "$STAGE/web-dist/." "$NEW_WEB/"
sudo chown -R admin:admin "$NEW_WEB"

sudo install -o root -g root -m 0644 "$STAGE/nginx.conf.rendered" "$NGINX_TARGET"
NGINX_CHANGED=true
sudo nginx -t

sudo systemctl stop "$SERVICE"
sudo install -o admin -g admin -m 0640 "$STAGE/orange-server.jar" "$APP_ROOT/orange-server.jar"
sudo mv "$APP_ROOT/web-dist" "$OLD_WEB"
sudo mv "$NEW_WEB" "$APP_ROOT/web-dist"
sudo systemctl start "$SERVICE"

for _ in {1..30}; do
  if curl -fsS "$HEALTH_URL" > /tmp/orangevc-health.json; then break; fi
  sleep 2
done
curl -fsS "$HEALTH_URL"
echo
sudo systemctl is-active --quiet "$SERVICE"
sudo systemctl reload nginx

PUBLIC_URL="https://$DOMAIN"
CURL_TARGET_ARGS=(--noproxy '*' --resolve "$DOMAIN:443:127.0.0.1")
assert_http_status "$PUBLIC_URL/market" 200
assert_http_status "$PUBLIC_URL/assets/release-check-$STAMP.js" 404
assert_http_status "$PUBLIC_URL/.env" 404
assert_content_type "$PUBLIC_URL/robots.txt" 'text/plain'
assert_content_type "$PUBLIC_URL/sitemap.xml" '(application|text)/xml'
assert_content_type "$PUBLIC_URL/.well-known/security.txt" 'text/plain'
HEADERS="$(curl "${CURL_TARGET_ARGS[@]}" --max-time 15 -fsSI "$PUBLIC_URL/market" | tr -d '\r')"
grep -Eiq '^Strict-Transport-Security:' <<< "$HEADERS"
grep -Eiq '^Content-Security-Policy:' <<< "$HEADERS"
grep -Eiq '^X-Content-Type-Options:[[:space:]]*nosniff' <<< "$HEADERS"

trap - ERR
echo "Deployment succeeded"
echo "Backup: $BACKUP"
echo "Archive SHA256: $ACTUAL_SHA"
