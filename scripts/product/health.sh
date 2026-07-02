#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

product_load_env

failures=0

while IFS= read -r url; do
  [ -n "$url" ] || continue
  if curl -fsS "$url" >/dev/null; then
    echo "healthy: $url"
  else
    echo "unhealthy: $url" >&2
    failures=$((failures + 1))
  fi
done < <(product_health_checks)

livekit_host="${LIVEKIT_HTTP_URL:-http://localhost:7880}"
if curl -fsS "$livekit_host" >/dev/null 2>&1 || curl -fsS -o /dev/null "$livekit_host/" 2>/dev/null; then
  echo "healthy: $livekit_host (LiveKit)"
else
  # LiveKit dev server may not expose a friendly HTTP health route; accept an open TCP port.
  if command -v nc >/dev/null 2>&1 && nc -z localhost 7880 2>/dev/null; then
    echo "healthy: LiveKit port 7880"
  else
    echo "unhealthy: LiveKit port 7880" >&2
    failures=$((failures + 1))
  fi
fi

if [ "$failures" -gt 0 ]; then
  echo "$failures health check(s) failed" >&2
  exit 1
fi

echo "Product stack health checks passed."
