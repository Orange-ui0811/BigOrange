#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build"
STAGE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/orangejd-release.XXXXXX")"
trap 'rm -rf -- "$STAGE_DIR"' EXIT

command -v mvn >/dev/null 2>&1 || { echo "Maven 3.9+ is required" >&2; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "Node.js/npm is required" >&2; exit 1; }
if [[ "${ALLOW_DIRTY_RELEASE:-false}" != "true" ]] && [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to build an untraceable release from a dirty Git worktree" >&2
  exit 1
fi
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
npm --prefix orange-web audit --omit=dev --audit-level=high
npm --prefix orange-web run lint
npm --prefix orange-web run typecheck
npm --prefix orange-web test -- --run
npm --prefix orange-web run build
npm --prefix orange-admin ci
npm --prefix orange-admin audit --omit=dev --audit-level=high
npm --prefix orange-admin run lint
npm --prefix orange-admin run typecheck
npm --prefix orange-admin test -- --run
npm --prefix orange-admin run build
node scripts/check-frontend-bundles.mjs

mkdir -p "$STAGE_DIR/web-dist/admin" "$STAGE_DIR/config" "$STAGE_DIR/database"
install -m 0644 orange-server/target/orange-server-1.0.0-SNAPSHOT.jar "$STAGE_DIR/orange-server.jar"
cp -R orange-web/dist/. "$STAGE_DIR/web-dist/"
cp -R orange-admin/dist/. "$STAGE_DIR/web-dist/admin/"
printf '%s' "$VITE_TURNSTILE_SITE_KEY" \
  | sha256sum \
  | awk '{print $1}' \
  > "$STAGE_DIR/config/turnstile-site-key.sha256"
install -m 0644 .env.prod.example "$STAGE_DIR/config/orangejd.env.example"
install -m 0644 nginx.conf "$STAGE_DIR/config/nginx.conf.example"
install -m 0644 scripts/orangejd.service "$STAGE_DIR/config/orangejd.service.example"
cp -R orange-server/src/main/resources/db/migration "$STAGE_DIR/database/"

GIT_SHA="$(git rev-parse HEAD)"
GIT_REF="$(git branch --show-current)"
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
JAR_SHA="$(sha256sum "$STAGE_DIR/orange-server.jar" | awk '{print $1}')"
WEB_SHA="$(sha256sum "$STAGE_DIR/web-dist/index.html" | awk '{print $1}')"
ADMIN_SHA="$(sha256sum "$STAGE_DIR/web-dist/admin/index.html" | awk '{print $1}')"
find "$STAGE_DIR/database/migration" -maxdepth 1 -name 'V*.sql' -printf '%f\n' \
  | sort -V > "$STAGE_DIR/database/migrations.txt"

cat > "$STAGE_DIR/manifest.json" <<EOF
{
  "product": "OrangeVC",
  "gitSha": "$GIT_SHA",
  "gitRef": "$GIT_REF",
  "builtAt": "$BUILD_TIME",
  "sha256": {
    "orange-server.jar": "$JAR_SHA",
    "web-dist/index.html": "$WEB_SHA",
    "web-dist/admin/index.html": "$ADMIN_SHA"
  }
}
EOF

mkdir -p "$BUILD_DIR"
ARCHIVE_TMP="$BUILD_DIR/orangejd-release.tar.gz.tmp"
tar -C "$STAGE_DIR" -czf "$ARCHIVE_TMP" .
mv -f "$ARCHIVE_TMP" "$BUILD_DIR/orangejd-release.tar.gz"
install -m 0755 scripts/install-release.sh "$BUILD_DIR/install-release.sh"
sha256sum "$BUILD_DIR/orangejd-release.tar.gz" \
  > "$BUILD_DIR/orangejd-release.tar.gz.sha256"
echo "Release created: $BUILD_DIR/orangejd-release.tar.gz"
cat "$BUILD_DIR/orangejd-release.tar.gz.sha256"
