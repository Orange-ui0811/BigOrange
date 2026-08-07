#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/lib/production-config.sh"

set_valid_config() {
  export VITE_API_BASE_URL=/api VITE_WS_URL=
  export VITE_TURNSTILE_SITE_KEY=0x4AAAAA-valid-production-key
  export VITE_LEGAL_OPERATOR='OrangeVC Test Operator'
  export VITE_LEGAL_CONTACT_EMAIL=security@orangevf.com
  export VITE_LEGAL_REGISTERED_ADDRESS= VITE_LEGAL_CREDIT_CODE=
  export VITE_LEGAL_ICP_RECORD='粤ICP备00000000号'
  export VITE_LEGAL_PUBLIC_SECURITY_RECORD=
  export VITE_OFFICIAL_SITE_URL=https://orangevf.com
  export VITE_TRANSACTION_UI_ENABLED=false
}

assert_rejected() {
  if validate_public_build_config >/dev/null 2>&1; then
    echo "Expected invalid config to be rejected: $1" >&2
    exit 1
  fi
}

set_valid_config
validate_public_build_config
first="$(public_build_config_fingerprint)"
[[ ${#first} -eq 64 ]]
VITE_LEGAL_OPERATOR=YOUR_LEGAL_ENTITY_NAME; assert_rejected placeholder
set_valid_config; VITE_LEGAL_CONTACT_EMAIL=support@example.com; assert_rejected example-email
set_valid_config; VITE_LEGAL_ICP_RECORD=; assert_rejected missing-icp
set_valid_config; VITE_OFFICIAL_SITE_URL=https://example.org; assert_rejected wrong-site
set_valid_config; VITE_TURNSTILE_SITE_KEY=1x00000000000000000000AA; assert_rejected test-key
set_valid_config
[[ "$first" == "$(public_build_config_fingerprint)" ]]
VITE_LEGAL_OPERATOR='Another Operator'
[[ "$first" != "$(public_build_config_fingerprint)" ]]
echo "production config contract tests passed"
