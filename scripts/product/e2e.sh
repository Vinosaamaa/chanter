#!/usr/bin/env bash
# Product Playwright suite (#103): up → seed → test → down (unless KEEP_STACK=1)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

MODE="${1:-critical}" # critical | product

cleanup() {
  if [ "${KEEP_STACK:-0}" != "1" ] && [ "$MODE" = "product" ]; then
    make product-down || true
  fi
}
trap cleanup EXIT

if [ "$MODE" = "product" ]; then
  make product-up
  make product-health
  make product-demo-seed
  export PLAYWRIGHT_PRODUCT=1
  export PLAYWRIGHT_SKIP_WEBSERVER=1
  export PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:5173}"
  (cd frontend && npx playwright test --grep @product)
else
  node scripts/check-no-dead-controls.mjs
  (cd frontend && npx playwright test --grep @critical)
fi
