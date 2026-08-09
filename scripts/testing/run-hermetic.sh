#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -eq 0 ]; then
  echo "usage: $0 command [args...]" >&2
  exit 2
fi

unset_args=()
while IFS='=' read -r name _; do
  case "$name" in
    CHANTER_*|SPRING_*|POSTGRES_*|REDIS_*|KAFKA_*|MINIO_*|LIVEKIT_*|\
    GATEWAY_*|AUTH_*|COMMUNITY_*|MESSAGE_*|MEDIA_*|AGENT_*|ANALYTICS_*|\
    SEARCH_*|NOTIFICATION_*|REALTIME_*|FRONTEND_*|DEMO_*|VITE_*|OPENAI_*|OLLAMA_*)
      unset_args+=("-u" "$name")
      ;;
  esac
done < <(env)

exec env "${unset_args[@]}" "$@"
