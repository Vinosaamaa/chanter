#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUNNER="$ROOT/scripts/testing/run-hermetic.sh"

actual="$({
  CHANTER_JWT_SECRET=poison-jwt \
  REDIS_PASSWORD=poison-redis \
  SPRING_PROFILES_ACTIVE=production \
  TEST_RUNNER_SENTINEL=preserved \
    "$RUNNER" bash -c 'printf "%s|%s|%s|%s" \
      "${CHANTER_JWT_SECRET-unset}" \
      "${REDIS_PASSWORD-unset}" \
      "${SPRING_PROFILES_ACTIVE-unset}" \
      "${TEST_RUNNER_SENTINEL-unset}"'
})"

expected='unset|unset|unset|preserved'
if [ "$actual" != "$expected" ]; then
  echo "FAIL: hermetic test process expected '$expected', got '$actual'" >&2
  exit 1
fi

echo "ok: hermetic test process rejects runtime configuration"
