#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

ROOT="$(product_repo_root)"
product_load_env || true
product_ensure_state_dirs

product_stop_supervisor

while IFS= read -r module; do
  [ -n "$module" ] || continue
  product_stop_module "$module"
done < <(product_java_modules)

product_stop_module frontend

echo "Stopping product Docker services (realtime, LiveKit)..."
docker compose -f "$ROOT/infra/docker-compose.yml" --env-file "$ROOT/.env" --profile product \
  stop realtime-service livekit >/dev/null 2>&1 || true

echo "Product app processes stopped. Core infra (Postgres, Redis, broker, MinIO) is still running."
echo "Run 'make infra-down' to stop infrastructure."
