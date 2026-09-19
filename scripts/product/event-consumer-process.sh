#!/usr/bin/env bash
# Test-only control of the exact consumer processes started by this checkout.
set -euo pipefail
source "$(dirname "$0")/lib.sh"
product_load_env
action="${1:?action required}"
module="${2:?module required}"
case "$module" in search-service|notification-service) ;; *) exit 2 ;; esac
root="$(product_repo_root)"
pid_file="$(product_pids_dir)/${module}.pid"
marker="$(product_state_dir)/${module}.event-drill-stopped"
case "$action" in
  stop)
    pid="$(cat "$pid_file")"
    [[ "$pid" =~ ^[0-9]+$ ]] || exit 2
    jar="$(product_module_jar "$module")"
    # Receipt and pidfd checks refuse stale/reused PIDs without a port-listener fallback.
    python3 "$root/scripts/product/stop-event-consumer.py" "$pid_file" "$root/backend" "$jar"
    for _ in {1..100}; do
      if ! product_is_port_listening "$(product_module_port "$module")"; then break; fi
      sleep 0.1
    done
    ! product_is_port_listening "$(product_module_port "$module")" || exit 4
    cp -- "$pid_file.start" "$marker"
    rm -- "$pid_file" "$pid_file.start"
    ;;
  start)
    [ -f "$marker" ] || exit 3
    ! product_is_port_listening "$(product_module_port "$module")" || exit 4
    product_start_java_module "$module" detached
    rm -- "$marker"
    ;;
  *) exit 2 ;;
esac
