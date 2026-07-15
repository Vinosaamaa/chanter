#!/usr/bin/env bash
# Smoke-check LLM provider reachability for local/staging (#97).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

ENABLED="${CHANTER_LLM_ENABLED:-false}"
PROVIDER="${CHANTER_LLM_PROVIDER:-ollama}"
TOKEN="${CHANTER_INTERNAL_SERVICE_TOKEN:-}"
AGENT_URL="${AGENT_SERVICE_URL:-http://localhost:8085}"

echo "CHANTER_LLM_ENABLED=$ENABLED provider=$PROVIDER"

if [ "$ENABLED" != "true" ]; then
  echo "LLM disabled — OK (default). Enable with CHANTER_LLM_ENABLED=true when a provider is running."
  exit 0
fi

if [ -z "$TOKEN" ]; then
  echo "CHANTER_INTERNAL_SERVICE_TOKEN is required to probe agent-service" >&2
  exit 1
fi

curl -fsS "$AGENT_URL/api/v1/internal/llm/health" \
  -H "X-Chanter-Internal-Service-Token: $TOKEN" | tee /tmp/chanter-llm-health.json
echo
python3 - <<'PY'
import json,sys
data=json.load(open("/tmp/chanter-llm-health.json"))
print(data)
if not data.get("enabled"):
    sys.exit("expected enabled=true")
if not data.get("reachable"):
    sys.exit("provider not reachable — start Ollama or check OPENAI_API_KEY / base URL")
print("LLM provider smoke OK")
PY
