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
# Realtime runs on the host (with other Java services) so it can reach community/message on localhost.
docker compose -f "$COMPOSE_FILE" --env-file "$ROOT/.env" --profile product stop realtime-service >/dev/null 2>&1 || true
docker compose -f "$COMPOSE_FILE" --env-file "$ROOT/.env" --profile product up -d --wait --wait-timeout 180 \
  postgres redis redpanda minio livekit

echo "Infrastructure is healthy."

echo "Building backend modules (skip tests)..."
(cd "$ROOT/backend" && mvn -B -q install -DskipTests)

start_java_module() {
  local module="$1"
  local pid_file
  pid_file="$(product_pids_dir)/${module}.pid"
  local log_file
  log_file="$(product_logs_dir)/${module}.log"

  local port
  port="$(product_module_port "$module")"
  if product_is_port_listening "$port"; then
    echo "already running: $module (port $port)"
  else
    local jar
    jar="$(product_module_jar "$module")"
    echo "starting: $module"
    nohup java -jar "$jar" >"$log_file" 2>&1 &
    echo $! >"$pid_file"
    disown -h
    product_wait_for_port "$port" "$module"
  fi
}

while IFS= read -r module; do
  [ -n "$module" ] || continue
  if [ "$module" = gateway-service ] || [ "$module" = realtime-service ]; then
    continue
  fi
  start_java_module "$module"
  sleep 2
done < <(product_java_modules)

start_java_module realtime-service
sleep 2

start_java_module gateway-service

product_wait_for_url "$(product_gateway_url)/actuator/health" "gateway"

frontend_pid_file="$(product_pids_dir)/frontend.pid"
frontend_port="$(product_module_port frontend)"
if product_is_port_listening "$frontend_port"; then
  echo "already running: frontend (port $frontend_port)"
else
  if [ ! -d "$ROOT/frontend/node_modules" ]; then
    echo "Installing frontend dependencies..."
    (cd "$ROOT/frontend" && npm install)
  fi
  echo "starting: frontend"
  cd "$ROOT/frontend"
  nohup npm run dev -- --host 127.0.0.1 --port "$frontend_port" \
    >"$(product_logs_dir)/frontend.log" 2>&1 &
  echo $! >"$frontend_pid_file"
  disown -h
  cd "$ROOT"
  product_wait_for_port "$frontend_port" "frontend" 30 1
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
