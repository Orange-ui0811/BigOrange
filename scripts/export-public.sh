#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="${1:-$(dirname -- "$SOURCE_DIR")/BigOrange-public}"

if [[ -e "$DEST_DIR" ]]; then
  echo "Refusing to overwrite existing destination: $DEST_DIR" >&2
  exit 1
fi

command -v rsync >/dev/null 2>&1 || { echo "rsync is required" >&2; exit 1; }
command -v rg >/dev/null 2>&1 || { echo "ripgrep (rg) is required" >&2; exit 1; }

TEMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/bigorange-public.XXXXXX")"
SCAN_FILE="$(mktemp "${TMPDIR:-/tmp}/bigorange-scan.XXXXXX")"
trap 'rm -rf -- "$TEMP_DIR"; rm -f -- "$SCAN_FILE"' EXIT

rsync -a \
  --exclude='.git/' \
  --exclude='.agents/' \
  --exclude='.claude/' \
  --exclude='.codex/' \
  --exclude='.codegraph/' \
  --exclude='.idea/' \
  --exclude='.vscode/' \
  --exclude='node_modules/' \
  --exclude='target/' \
  --exclude='dist/' \
  --exclude='build/' \
  --exclude='deploy/' \
  --exclude='backup/' \
  --exclude='uploads/' \
  --exclude='test-frontend.html' \
  --exclude='*.log' \
  --exclude='*.pem' \
  --exclude='*.key' \
  --exclude='*.p12' \
  --exclude='*.pfx' \
  --exclude='*.jks' \
  --include='.env.example' \
  --include='.env.*.example' \
  --exclude='.env' \
  --exclude='.env.*' \
  --exclude='orangejd-skeleton/' \
  --exclude='docs/开发日志/' \
  --exclude='docs/当前生产部署与运维手册.md' \
  --exclude='docs/项目进度记录.md' \
  "$SOURCE_DIR/" "$TEMP_DIR/"

if find "$TEMP_DIR" \( -name .git -o -name '*.pem' -o -name '*.key' \) -print -quit | grep -q .; then
  echo "Export contains forbidden Git or key material" >&2
  exit 1
fi

# Report filenames only, never matching values.
if rg -l --hidden --glob '!**/scripts/export-public.sh' \
  '(BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|/Users/[^/[:space:]]+|[A-Za-z]:\\Users\\|"password"[[:space:]]*:[[:space:]]*"123456")' \
  "$TEMP_DIR" >"$SCAN_FILE" 2>/dev/null; then
  echo "Public export blocked; sensitive-looking files:" >&2
  sed "s#^$TEMP_DIR/##" "$SCAN_FILE" >&2
  exit 1
fi

mv "$TEMP_DIR" "$DEST_DIR"
rm -f -- "$SCAN_FILE"
trap - EXIT
echo "Public source created: $DEST_DIR"
