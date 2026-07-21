#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build"
STAGE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/orangejd-release.XXXXXX")"
trap 'rm -rf -- "$STAGE_DIR"' EXIT

command -v mvn >/dev/null 2>&1 || { echo "Maven 3.9+ is required" >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "Node.js/npm is required" >&2; exit 1; }
: "${VITE_TURNSTILE_SITE_KEY:?Set the public Turnstile site key for the production build}"
: "${VITE_LEGAL_OPERATOR:?Set the legal operator name for the production build}"
: "${VITE_LEGAL_CONTACT_EMAIL:?Set the legal contact email for the production build}"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-/api}"
export VITE_WS_URL="${VITE_WS_URL:-}"

# Git for Windows otherwise rewrites values such as /api into a local MSYS path
# when npm is launched from this script.
case "$(uname -s)" in
  MINGW*|MSYS*)
    export MSYS2_ENV_CONV_EXCL="${MSYS2_ENV_CONV_EXCL:+$MSYS2_ENV_CONV_EXCL;}VITE_API_BASE_URL;VITE_WS_URL"
    ;;
esac

cd "$ROOT_DIR"
if [[ "${SKIP_TESTS:-false}" == "true" ]]; then
  mvn -B clean package -DskipTests
else
  mvn -B clean verify
fi

npm --prefix orange-web ci
npm --prefix orange-web run build
npm --prefix orange-admin ci
npm --prefix orange-admin run build

mkdir -p "$STAGE_DIR/web-dist/admin" "$STAGE_DIR/config" "$STAGE_DIR/database"
install -m 0644 orange-server/target/orange-server-1.0.0-SNAPSHOT.jar "$STAGE_DIR/orange-server.jar"
cp -R orange-web/dist/. "$STAGE_DIR/web-dist/"
cp -R orange-admin/dist/. "$STAGE_DIR/web-dist/admin/"
install -m 0644 .env.prod.example "$STAGE_DIR/config/orangejd.env.example"
install -m 0644 nginx.conf "$STAGE_DIR/config/nginx.conf.example"
install -m 0644 scripts/orangejd.service "$STAGE_DIR/config/orangejd.service.example"
install -m 0644 orange-server/src/main/resources/sql/schema-v1.sql "$STAGE_DIR/database/schema-v1.sql"
cp -R orange-server/src/main/resources/db/migration "$STAGE_DIR/database/"

mkdir -p "$BUILD_DIR"
ARCHIVE_TMP="$BUILD_DIR/orangejd-release.tar.gz.tmp"
tar -C "$STAGE_DIR" -czf "$ARCHIVE_TMP" .
mv -f "$ARCHIVE_TMP" "$BUILD_DIR/orangejd-release.tar.gz"
echo "Release created: $BUILD_DIR/orangejd-release.tar.gz"
