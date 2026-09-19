#!/bin/sh
set -eu
umask 007

# Finish the update before loading the engine. Failure leaves scanning unavailable.
freshclam --config-file=/etc/clamav/freshclam.conf --foreground --stdout
freshclam --config-file=/etc/clamav/freshclam.conf --daemon --foreground --stdout &
updater=$!
clamd --config-file=/etc/clamav/clamd.conf --foreground &
scanner=$!
cleanup() {
  trap - EXIT INT TERM
  kill "$updater" "$scanner" 2>/dev/null || true
  wait "$updater" "$scanner" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 0' INT TERM
# Losing either process must restart the pair. Per-upload checks also reject stale definitions.
while kill -0 "$updater" 2>/dev/null && kill -0 "$scanner" 2>/dev/null; do sleep 2; done
exit 1
