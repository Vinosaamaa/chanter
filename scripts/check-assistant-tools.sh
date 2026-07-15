#!/usr/bin/env bash
# Demonstrate MCP-compatible assistant tools locally (#99).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

TOKEN="${CHANTER_INTERNAL_SERVICE_TOKEN:-}"
AGENT_URL="${AGENT_SERVICE_URL:-http://localhost:8085}"

if [ -z "$TOKEN" ]; then
  echo "CHANTER_INTERNAL_SERVICE_TOKEN is required" >&2
  exit 1
fi

echo "Listing assistant tools from $AGENT_URL ..."
curl -fsS "$AGENT_URL/api/v1/internal/assistant-tools" \
  -H "X-Chanter-Internal-Service-Token: $TOKEN" | tee /tmp/chanter-assistant-tools.json
echo

python3 - <<'PY'
import json,sys
data=json.load(open("/tmp/chanter-assistant-tools.json"))
names={t["name"] for t in data.get("tools",[])}
required={"list_granted_resources","fetch_resource_chunk","search_course_faq"}
missing=required-names
if missing:
    sys.exit(f"missing tools: {sorted(missing)}")
print("Assistant tools smoke OK:", ", ".join(sorted(names)))
PY

echo
echo "MCP tools/list:"
curl -fsS "$AGENT_URL/api/v1/internal/assistant-tools/mcp" \
  -H "X-Chanter-Internal-Service-Token: $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
echo
