#!/usr/bin/env bash
# Sticky product stack for long-lived shells (e.g. Cursor agent background terminals).
# Services are launched with PID tracking; they survive supervisor shell exit as detached processes.
# Use `make product-down` to stop the stack.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

product_load_env
product_configure_java_home
product_ensure_state_dirs
product_require_lsof

supervisor_pid_file="$(product_supervisor_pid_file)"
if product_is_pid_running "$supervisor_pid_file"; then
  existing_pid="$(cat "$supervisor_pid_file")"
  echo "Chanter product supervisor already running (pid $existing_pid)."
  echo "Verify: make product-health"
  exit 0
fi

echo $$ >"$supervisor_pid_file"
trap 'rm -f "$supervisor_pid_file"' EXIT INT TERM

product_prepare_infrastructure
product_build_backend
product_start_application_stack supervised

supervisor_note="Supervisor: pid $$ (stack stays up while this process runs — use make product-down to stop)"
product_print_stack_ready_message "$supervisor_note"

echo "Supervisor watching stack health every 60s (logs only; no auto-restart)."

while true; do
  sleep 60
  unhealthy=""
  while IFS= read -r check_url; do
    [ -n "$check_url" ] || continue
    if ! curl -fsS "$check_url" >/dev/null 2>&1; then
      unhealthy="${unhealthy} ${check_url}"
    fi
  done < <(product_health_checks)

  if [ -n "$unhealthy" ]; then
    echo "warning: supervisor detected unhealthy endpoints:${unhealthy}" >&2
  fi
done
