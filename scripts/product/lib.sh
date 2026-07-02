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
gateway-service
EOF
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

product_is_pid_running() {
  local pid_file="$1"
  if [ ! -f "$pid_file" ]; then
    return 1
  fi
  local pid
  pid="$(cat "$pid_file")"
  kill -0 "$pid" 2>/dev/null
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
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill -TERM "$pid" 2>/dev/null || true
    sleep 1
    pkill -KILL -P "$pid" 2>/dev/null || true
    kill -KILL "$pid" 2>/dev/null || true
    echo "stopped: $name"
  fi
  rm -f "$pid_file"
}
