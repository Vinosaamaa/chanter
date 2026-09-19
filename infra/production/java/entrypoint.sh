#!/bin/sh
set -eu
if [ "${1:-}" = migrate ]; then
  exec java -cp '/app/helpers:/app/classes:/app/lib/*' Migrate
fi
if [ "$#" -ne 0 ]; then
  echo 'Unsupported application command' >&2
  exit 2
fi
if [ "${CHANTER_TELEMETRY_ENABLED:-false}" = true ]; then
  exec java -javaagent:/app/telemetry/agent.jar -cp '/app/classes:/app/lib/*' "$(cat /app/main-class)"
fi
exec java -cp '/app/classes:/app/lib/*' "$(cat /app/main-class)"
