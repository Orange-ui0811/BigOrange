#!/usr/bin/env bash

PUBLIC_BUILD_CONFIG_KEYS=(
  VITE_API_BASE_URL VITE_WS_URL VITE_TURNSTILE_SITE_KEY
  VITE_LEGAL_OPERATOR VITE_LEGAL_CONTACT_EMAIL VITE_LEGAL_REGISTERED_ADDRESS
  VITE_LEGAL_CREDIT_CODE VITE_LEGAL_ICP_RECORD VITE_LEGAL_PUBLIC_SECURITY_RECORD
  VITE_OFFICIAL_SITE_URL VITE_TRANSACTION_UI_ENABLED VITE_REGISTRATION_ENABLED
)

apply_public_build_defaults() {
  export VITE_API_BASE_URL="${VITE_API_BASE_URL:-/api}"
  export VITE_WS_URL="${VITE_WS_URL:-}"
  export VITE_LEGAL_REGISTERED_ADDRESS="${VITE_LEGAL_REGISTERED_ADDRESS:-}"
  export VITE_LEGAL_CREDIT_CODE="${VITE_LEGAL_CREDIT_CODE:-}"
  export VITE_LEGAL_PUBLIC_SECURITY_RECORD="${VITE_LEGAL_PUBLIC_SECURITY_RECORD:-}"
  export VITE_TRANSACTION_UI_ENABLED="${VITE_TRANSACTION_UI_ENABLED:-false}"
  export VITE_REGISTRATION_ENABLED="${VITE_REGISTRATION_ENABLED:-true}"
}

invalid_public_value() {
  local value="${1:-}"
  [[ "$value" == *YOUR_* || "$value" == *your_* || "$value" == *example.com* \
    || "$value" == *localhost* || "$value" == *127.0.0.1* ]]
}

validate_public_build_config() {
  apply_public_build_defaults
  local required=(VITE_TURNSTILE_SITE_KEY VITE_LEGAL_OPERATOR VITE_LEGAL_CONTACT_EMAIL VITE_LEGAL_ICP_RECORD VITE_OFFICIAL_SITE_URL)
  local key value
  for key in "${required[@]}"; do
    value="${!key:-}"
    [[ -n "$value" ]] || { echo "$key must be configured" >&2; return 1; }
  done
  for key in "${PUBLIC_BUILD_CONFIG_KEYS[@]}"; do
    value="${!key:-}"
    if [[ -n "$value" ]] && invalid_public_value "$value"; then
      echo "$key contains a production placeholder or local address" >&2
      return 1
    fi
  done
  [[ "$VITE_API_BASE_URL" == "/api" ]] || { echo "VITE_API_BASE_URL must be /api" >&2; return 1; }
  [[ "$VITE_OFFICIAL_SITE_URL" == "https://orangevf.com" ]] || { echo "VITE_OFFICIAL_SITE_URL must be https://orangevf.com" >&2; return 1; }
  [[ "$VITE_LEGAL_CONTACT_EMAIL" =~ ^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$ ]] || { echo "VITE_LEGAL_CONTACT_EMAIL is invalid" >&2; return 1; }
  [[ "$VITE_TURNSTILE_SITE_KEY" != "1x00000000000000000000AA" ]] || { echo "Turnstile test keys are forbidden in production" >&2; return 1; }
  [[ "$VITE_TRANSACTION_UI_ENABLED" == "true" || "$VITE_TRANSACTION_UI_ENABLED" == "false" ]] || { echo "VITE_TRANSACTION_UI_ENABLED must be true or false" >&2; return 1; }
  [[ "$VITE_REGISTRATION_ENABLED" == "true" || "$VITE_REGISTRATION_ENABLED" == "false" ]] || { echo "VITE_REGISTRATION_ENABLED must be true or false" >&2; return 1; }
}

emit_public_build_config() {
  local key
  for key in "${PUBLIC_BUILD_CONFIG_KEYS[@]}"; do
    printf '%s=%s\n' "$key" "${!key:-}"
  done
}

public_build_config_fingerprint() {
  emit_public_build_config | sha256sum | awk '{print $1}'
}

read_env_file_value() {
  local file="$1" key="$2" default_value="${3:-}" value
  value="$(sed -n "s/^${key}=//p" "$file" | tail -n 1 | tr -d '\r')"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "${value:-$default_value}"
}

load_public_build_config_from_env_file() {
  local file="$1" key value
  [[ -r "$file" ]] || { echo "Cannot read production environment file: $file" >&2; return 1; }
  for key in "${PUBLIC_BUILD_CONFIG_KEYS[@]}"; do
    case "$key" in
      VITE_API_BASE_URL) value="$(read_env_file_value "$file" "$key" "/api")" ;;
      VITE_TRANSACTION_UI_ENABLED) value="$(read_env_file_value "$file" "$key" "false")" ;;
      VITE_REGISTRATION_ENABLED) value="$(read_env_file_value "$file" "$key" "true")" ;;
      *) value="$(read_env_file_value "$file" "$key")" ;;
    esac
    printf -v "$key" '%s' "$value"
    export "$key"
  done
  apply_public_build_defaults
}
