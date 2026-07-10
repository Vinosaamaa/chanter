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

java_modules="$(product_java_modules | tr '\n' ' ')"
assert_contains "auth module" "auth-service" "$java_modules"
assert_contains "gateway module" "gateway-service" "$java_modules"
assert_contains "search module" "search-service" "$java_modules"
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
