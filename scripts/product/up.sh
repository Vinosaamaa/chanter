#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

ROOT="$(product_repo_root)"
product_load_env
product_configure_java_home
product_ensure_state_dirs

COMPOSE_FILE="$ROOT/infra/docker-compose.yml"

echo "Starting Chanter product infrastructure..."
docker compose -f "$COMPOSE_FILE" --env-file "$ROOT/.env" --profile product up -d \
  postgres redis redpanda minio realtime-service livekit

echo "Waiting for infrastructure health..."
docker compose -f "$COMPOSE_FILE" --env-file "$ROOT/.env" --profile product \
  exec -T postgres pg_isready -U "${POSTGRES_USER:-chanter}" -d "${POSTGRES_DB:-chanter}" >/dev/null

echo "Building backend modules (skip tests)..."
(cd "$ROOT/backend" && mvn -B -q install -DskipTests)

start_java_module() {
  local module="$1"
  local pid_file
  pid_file="$(product_pids_dir)/${module}.pid"
  local log_file
  log_file="$(product_logs_dir)/${module}.log"

  if product_is_pid_running "$pid_file"; then
    echo "already running: $module (pid $(cat "$pid_file"))"
    return 0
  fi

  echo "starting: $module"
  (
    cd "$ROOT/backend"
    nohup mvn -B -q -pl "$module" spring-boot:run >"$log_file" 2>&1 &
    echo $! >"$pid_file"
  )
}

while IFS= read -r module; do
  [ -n "$module" ] || continue
  if [ "$module" = gateway-service ]; then
    continue
  fi
  start_java_module "$module"
  sleep 2
done < <(product_java_modules)

start_java_module gateway-service

product_wait_for_url "$(product_gateway_url)/actuator/health" "gateway"

frontend_pid_file="$(product_pids_dir)/frontend.pid"
if product_is_pid_running "$frontend_pid_file"; then
  echo "already running: frontend (pid $(cat "$frontend_pid_file"))"
else
  if [ ! -d "$ROOT/frontend/node_modules" ]; then
    echo "Installing frontend dependencies..."
    (cd "$ROOT/frontend" && npm install)
  fi
  echo "starting: frontend"
  (
    cd "$ROOT/frontend"
    nohup npm run dev -- --host 127.0.0.1 --port "${FRONTEND_PORT:-5173}" \
      >"$(product_logs_dir)/frontend.log" 2>&1 &
    echo $! >"$frontend_pid_file"
  )
fi

product_wait_for_url "$(product_frontend_url)" "frontend" 30 2

cat <<EOF

Chanter product stack is up.

  Frontend:  $(product_frontend_url)
  Gateway:   $(product_gateway_url)
  Realtime:  http://localhost:${REALTIME_PORT:-8087}
  LiveKit:   $(product_livekit_url) (media plane for #61)

Logs:      $(product_logs_dir)/
Stop:      make product-down
Verify:    make product-health

EOF
