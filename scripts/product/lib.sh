#!/usr/bin/env bash

product_repo_root() {
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd
}

product_state_dir() {
  echo "$(product_repo_root)/.product"
}

product_logs_dir() {
  echo "$(product_state_dir)/logs"
}

product_pids_dir() {
  echo "$(product_state_dir)/pids"
}

product_frontend_url() {
  echo "http://localhost:${FRONTEND_PORT:-5173}"
}

product_gateway_url() {
  echo "http://localhost:${GATEWAY_PORT:-8080}"
}

product_livekit_url() {
  echo "${LIVEKIT_URL:-ws://localhost:7880}"
}

product_java_modules() {
  cat <<'EOF'
auth-service
community-service
message-service
media-service
agent-service
analytics-service
search-service
realtime-service
gateway-service
EOF
}

product_module_jar() {
  local module="$1"
  local jar
  jar="$(ls -t "$(product_repo_root)/backend/$module/target/${module}-"*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
  if [ -z "$jar" ]; then
    echo "missing jar for $module — run 'mvn install' in backend/" >&2
    return 1
  fi
  echo "$jar"
}

product_health_checks() {
  local gateway
  gateway="$(product_gateway_url)"
  cat <<EOF
${gateway}/actuator/health
${gateway}/api/v1/auth/health
http://localhost:${REALTIME_PORT:-8087}/actuator/health
EOF
}

product_configure_java_home() {
  if [ "$(uname -s)" = Darwin ]; then
    if /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
      export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
    elif /usr/libexec/java_home -v 23 >/dev/null 2>&1; then
      export JAVA_HOME="$(/usr/libexec/java_home -v 23)"
    else
      echo "Java 21 or 23 is required on macOS." >&2
      return 1
    fi
  fi
}

product_load_env() {
  local root
  root="$(product_repo_root)"
  if [ ! -f "$root/.env" ]; then
    cp "$root/.env.example" "$root/.env"
  fi
  set -a
  # shellcheck disable=SC1091
  source "$root/.env"
  set +a
  if [ -z "${CHANTER_JWT_SECRET:-}" ] || [ "${#CHANTER_JWT_SECRET}" -lt 32 ]; then
    echo "CHANTER_JWT_SECRET must be set to at least 32 characters in .env" >&2
    return 1
  fi
}

product_ensure_state_dirs() {
  mkdir -p "$(product_logs_dir)" "$(product_pids_dir)"
}

product_require_lsof() {
  if ! command -v lsof >/dev/null 2>&1; then
    echo "lsof is required for make product-up / product-down (e.g. brew install lsof)" >&2
    return 1
  fi
}

product_wait_for_url() {
  local url="$1"
  local label="$2"
  local attempts="${3:-60}"
  local delay="${4:-2}"
  local i=1
  while [ "$i" -le "$attempts" ]; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "ready: $label"
      return 0
    fi
    sleep "$delay"
    i=$((i + 1))
  done
  echo "timed out waiting for $label at $url" >&2
  return 1
}

product_wait_for_port() {
  local port="$1"
  local label="$2"
  local attempts="${3:-90}"
  local delay="${4:-2}"
  local i=1
  while [ "$i" -le "$attempts" ]; do
    if product_is_port_listening "$port"; then
      echo "ready: $label (port $port)"
      return 0
    fi
    sleep "$delay"
    i=$((i + 1))
  done
  echo "timed out waiting for $label on port $port" >&2
  return 1
}

product_module_port() {
  case "$1" in
    auth-service) echo "${AUTH_PORT:-8081}" ;;
    community-service) echo "${COMMUNITY_PORT:-8082}" ;;
    message-service) echo "${MESSAGE_PORT:-8083}" ;;
    media-service) echo "${MEDIA_PORT:-8084}" ;;
    agent-service) echo "${AGENT_PORT:-8085}" ;;
    analytics-service) echo "${ANALYTICS_PORT:-8086}" ;;
    gateway-service) echo "${GATEWAY_PORT:-8080}" ;;
    search-service) echo "${SEARCH_PORT:-8088}" ;;
    realtime-service) echo "${REALTIME_PORT:-8087}" ;;
    frontend) echo "${FRONTEND_PORT:-5173}" ;;
    *) return 1 ;;
  esac
}

product_port_listener_pid() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | head -1 || true
}

product_is_port_listening() {
  [ -n "$(product_port_listener_pid "$1")" ]
}

product_is_module_running() {
  product_is_port_listening "$(product_module_port "$1")"
}

product_is_pid_running() {
  local pid_file="$1"
  if [ ! -f "$pid_file" ]; then
    return 1
  fi
  local pid
  pid="$(cat "$pid_file")"
  kill -0 "$pid" 2>/dev/null
}

product_stop_pid_tree() {
  local pid="$1"
  pkill -TERM -P "$pid" 2>/dev/null || true
  kill -TERM "$pid" 2>/dev/null || true
  sleep 1
  pkill -KILL -P "$pid" 2>/dev/null || true
  kill -KILL "$pid" 2>/dev/null || true
}

product_stop_pid_file() {
  local pid_file="$1"
  local name="$2"
  if [ ! -f "$pid_file" ]; then
    return 0
  fi
  local pid
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    product_stop_pid_tree "$pid"
    echo "stopped: $name"
  fi
  rm -f "$pid_file"
}

product_stop_module() {
  local module="$1"
  local port listener_pid recorded_pid pid_file
  port="$(product_module_port "$module")"
  pid_file="$(product_pids_dir)/${module}.pid"
  recorded_pid=""
  if [ -f "$pid_file" ]; then
    recorded_pid="$(cat "$pid_file")"
  fi
  listener_pid="$(product_port_listener_pid "$port")"

  if [ -n "$recorded_pid" ] && kill -0 "$recorded_pid" 2>/dev/null; then
    product_stop_pid_tree "$recorded_pid"
    echo "stopped: $module (pid $recorded_pid)"
  elif [ -n "$listener_pid" ]; then
    if [ -n "$recorded_pid" ] && [ "$listener_pid" != "$recorded_pid" ]; then
      echo "warning: port $port is owned by pid $listener_pid (not $recorded_pid from $module); skipping kill" >&2
    else
      product_stop_pid_tree "$listener_pid"
      echo "stopped: $module (port $port)"
    fi
  elif [ -n "$recorded_pid" ]; then
    product_stop_pid_file "$pid_file" "$module"
  fi
  rm -f "$pid_file"
}

product_supervisor_pid_file() {
  echo "$(product_pids_dir)/supervisor.pid"
}

product_stop_supervisor() {
  product_stop_pid_file "$(product_supervisor_pid_file)" "supervisor"
}

# Fully detach a child process (double-fork + setsid) so it survives parent shell exit.
# Writes the child PID to pid_file and appends stdout/stderr to log_file.
product_spawn_detached() {
  local pid_file="$1"
  local log_file="$2"
  shift 2

  if [ -f "$pid_file" ]; then
    local existing_pid
    existing_pid="$(cat "$pid_file")"
    if kill -0 "$existing_pid" 2>/dev/null; then
      return 0
    fi
  fi

  mkdir -p "$(dirname "$pid_file")" "$(dirname "$log_file")"

  CHANTER_SPAWN_PID_FILE="$pid_file" \
  CHANTER_SPAWN_LOG_FILE="$log_file" \
  python3 - "$@" <<'PY'
import os
import subprocess
import sys

pid_file = os.environ["CHANTER_SPAWN_PID_FILE"]
log_file = os.environ["CHANTER_SPAWN_LOG_FILE"]
cmd = sys.argv[1:]

if os.fork() > 0:
    os._exit(0)
os.setsid()
if os.fork() > 0:
    os._exit(0)

os.umask(0o22)
with open(log_file, "ab", buffering=0) as log:
    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.DEVNULL,
        stdout=log,
        stderr=subprocess.STDOUT,
        close_fds=True,
        start_new_session=True,
    )
with open(pid_file, "w", encoding="utf-8") as handle:
    handle.write(str(proc.pid))
os._exit(0)
PY
}

product_prepare_infrastructure() {
  local root compose_file
  root="$(product_repo_root)"
  compose_file="$root/infra/docker-compose.yml"

  echo "Starting Chanter product infrastructure..."
  docker compose -f "$compose_file" --env-file "$root/.env" --profile product stop realtime-service >/dev/null 2>&1 || true
  docker compose -f "$compose_file" --env-file "$root/.env" --profile product up -d --wait --wait-timeout 180 \
    postgres redis redpanda minio livekit
  echo "Infrastructure is healthy."
}

product_build_backend() {
  local root
  root="$(product_repo_root)"
  echo "Building backend modules (skip tests)..."
  (cd "$root/backend" && mvn -B -q install -DskipTests)
}

product_start_java_module() {
  local module="$1"
  local spawn_mode="${2:-detached}"
  local root pid_file log_file port jar

  root="$(product_repo_root)"
  pid_file="$(product_pids_dir)/${module}.pid"
  log_file="$(product_logs_dir)/${module}.log"
  port="$(product_module_port "$module")"

  if product_is_port_listening "$port"; then
    echo "already running: $module (port $port)"
    return 0
  fi

  jar="$(product_module_jar "$module")"
  echo "starting: $module"

  if [ "$spawn_mode" = "supervised" ]; then
    (
      cd "$root/backend"
      java -jar "$jar" >>"$log_file" 2>&1 &
      echo $! >"$pid_file"
    )
  else
    product_spawn_detached "$pid_file" "$log_file" \
      bash -c "cd \"$(printf '%q' "$root/backend")\" && exec java -jar \"$(printf '%q' "$jar")\""
  fi

  product_wait_for_port "$port" "$module"
}

product_start_java_modules() {
  local spawn_mode="${1:-detached}"
  local module

  while IFS= read -r module; do
    [ -n "$module" ] || continue
    if [ "$module" = gateway-service ] || [ "$module" = realtime-service ]; then
      continue
    fi
    product_start_java_module "$module" "$spawn_mode"
    sleep 2
  done < <(product_java_modules)

  product_start_java_module realtime-service "$spawn_mode"
  sleep 2
  product_start_java_module gateway-service "$spawn_mode"
}

product_start_frontend() {
  local spawn_mode="${1:-detached}"
  local root frontend_pid_file frontend_port

  root="$(product_repo_root)"
  frontend_pid_file="$(product_pids_dir)/frontend.pid"
  frontend_port="$(product_module_port frontend)"

  if product_is_port_listening "$frontend_port"; then
    echo "already running: frontend (port $frontend_port)"
    return 0
  fi

  if [ ! -d "$root/frontend/node_modules" ]; then
    echo "Installing frontend dependencies..."
    (cd "$root/frontend" && npm install)
  fi

  echo "starting: frontend"
  if [ "$spawn_mode" = "supervised" ]; then
    (
      cd "$root/frontend"
      npm run dev -- --host 127.0.0.1 --port "$frontend_port" >>"$(product_logs_dir)/frontend.log" 2>&1 &
      echo $! >"$frontend_pid_file"
    )
  else
    product_spawn_detached "$frontend_pid_file" "$(product_logs_dir)/frontend.log" \
      bash -c "cd \"$(printf '%q' "$root/frontend")\" && exec npm run dev -- --host 127.0.0.1 --port \"$frontend_port\""
  fi

  product_wait_for_port "$frontend_port" "frontend" 30 1
}

product_start_application_stack() {
  local spawn_mode="${1:-detached}"
  product_start_java_modules "$spawn_mode"
  product_wait_for_url "$(product_gateway_url)/actuator/health" "gateway"
  product_start_frontend "$spawn_mode"
  product_wait_for_url "$(product_frontend_url)" "frontend" 30 2
}

product_print_stack_ready_message() {
  local supervisor_note="${1:-}"
  cat <<EOF

Chanter product stack is up.

  Frontend:  $(product_frontend_url)
  Gateway:   $(product_gateway_url)
  Realtime:  http://localhost:${REALTIME_PORT:-8087}
  LiveKit:   $(product_livekit_url) (media plane for #61)

Logs:      $(product_logs_dir)/
Stop:      make product-down
Verify:    make product-health
${supervisor_note}
EOF
}
