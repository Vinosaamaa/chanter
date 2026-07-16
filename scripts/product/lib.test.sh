#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

failures=0

assert_eq() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [ "$expected" != "$actual" ]; then
    echo "FAIL: $label (expected '$expected', got '$actual')" >&2
    failures=$((failures + 1))
  else
    echo "ok: $label"
  fi
}

assert_contains() {
  local label="$1"
  local needle="$2"
  local haystack="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "FAIL: $label (missing '$needle')" >&2
    failures=$((failures + 1))
  else
    echo "ok: $label"
  fi
}

assert_eq "frontend url" "http://localhost:5173" "$(product_frontend_url)"
assert_eq "gateway url" "http://localhost:8080" "$(product_gateway_url)"
assert_eq "livekit url" "ws://localhost:7880" "$(product_livekit_url)"

stale_env="$(mktemp)"
trap 'rm -f "$stale_env"' EXIT
printf '%s\n' \
  'CHANTER_JWT_SECRET=issue137-test-jwt-secret-at-least-32-chars' \
  'CHANTER_INTERNAL_SERVICE_TOKEN=issue137-test-service-token-at-least-32' \
  > "$stale_env"
livekit_defaults="$({
  unset LIVEKIT_URL LIVEKIT_HTTP_URL LIVEKIT_API_KEY LIVEKIT_API_SECRET
  export CHANTER_PRODUCT_ENV_FILE="$stale_env"
  product_load_env
  printf '%s|%s|%s|%s|%s' \
    "$LIVEKIT_URL" \
    "$LIVEKIT_HTTP_URL" \
    "$LIVEKIT_API_KEY" \
    "$LIVEKIT_API_SECRET" \
    "$CHANTER_JWT_SECRET"
})"
assert_eq "stale env receives LiveKit defaults" \
  "ws://localhost:7880|http://localhost:7880|devkey|secret|issue137-test-jwt-secret-at-least-32-chars" \
  "$livekit_defaults"

# --- SEC-04: reject known default secrets (not just length) ---
default_jwt_env="$(mktemp)"
printf '%s\n' \
  'CHANTER_JWT_SECRET=chanter-local-dev-jwt-secret-32bytes!!' \
  'CHANTER_INTERNAL_SERVICE_TOKEN=issue137-test-service-token-at-least-32' \
  > "$default_jwt_env"
default_jwt_rc=0
default_jwt_err="$(
  export CHANTER_PRODUCT_ENV_FILE="$default_jwt_env"
  product_load_env 2>&1
)" || default_jwt_rc=$?
assert_eq "rejects known default JWT secret (exit)" "1" "$default_jwt_rc"
assert_contains "rejects known default JWT secret (message)" "known default" "$default_jwt_err"
rm -f "$default_jwt_env"

default_token_env="$(mktemp)"
printf '%s\n' \
  'CHANTER_JWT_SECRET=issue137-test-jwt-secret-at-least-32-chars' \
  'CHANTER_INTERNAL_SERVICE_TOKEN=chanter-local-dev-internal-service-token-32bytes!!' \
  > "$default_token_env"
default_token_rc=0
default_token_err="$(
  export CHANTER_PRODUCT_ENV_FILE="$default_token_env"
  product_load_env 2>&1
)" || default_token_rc=$?
assert_eq "rejects known default internal token (exit)" "1" "$default_token_rc"
assert_contains "rejects known default internal token (message)" "known default" "$default_token_err"
rm -f "$default_token_env"

missing_env_dir="$(mktemp -d)"
missing_rc=0
missing_err="$(
  unset CHANTER_ALLOW_ENV_EXAMPLE_COPY
  export CHANTER_PRODUCT_ENV_FILE="$missing_env_dir/.env"
  product_load_env 2>&1
)" || missing_rc=$?
assert_eq "missing .env without opt-in fails (exit)" "1" "$missing_rc"
assert_contains "missing .env without opt-in (message)" "make product-env" "$missing_err"
rm -rf "$missing_env_dir"

java_modules="$(product_java_modules | tr '\n' ' ')"
assert_contains "auth module" "auth-service" "$java_modules"
assert_contains "gateway module" "gateway-service" "$java_modules"
assert_contains "search module" "search-service" "$java_modules"
assert_contains "notification module" "notification-service" "$java_modules"
assert_contains "realtime module" "realtime-service" "$java_modules"

health_checks="$(product_health_checks | tr '\n' ' ')"
assert_contains "gateway health" "http://localhost:8080/actuator/health" "$health_checks"
assert_contains "auth health via gateway" "http://localhost:8080/api/v1/auth/health" "$health_checks"
assert_contains "realtime health" "http://localhost:8087/actuator/health" "$health_checks"

if [ "$failures" -gt 0 ]; then
  echo "$failures test(s) failed" >&2
  exit 1
fi

echo "All product lib tests passed."
