#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_HOST:?Set MYSQL_HOST}"
: "${MYSQL_USER:?Set MYSQL_USER}"
: "${MYSQL_PASSWORD:?Set MYSQL_PASSWORD}"
: "${MYSQL_DB:?Set MYSQL_DB}"

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$ROOT_DIR/build/schema-snapshot.sql}"
mkdir -p "$(dirname -- "$TARGET")"

export MYSQL_PWD="$MYSQL_PASSWORD"
mysqldump \
  --host="$MYSQL_HOST" \
  --port="${MYSQL_PORT:-3306}" \
  --user="$MYSQL_USER" \
  --no-data \
  --skip-comments \
  --set-gtid-purged=OFF \
  "$MYSQL_DB" > "$TARGET.tmp"
unset MYSQL_PWD

mv -f "$TARGET.tmp" "$TARGET"
echo "Schema snapshot exported to $TARGET"
