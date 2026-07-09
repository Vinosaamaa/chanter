#!/usr/bin/env bash
# Remove duplicate Study Servers created during local demo testing.
# Keeps the newest "Workable Product Demo" server for dev-demo-owner@chanter.local.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$ROOT/scripts/product/lib.sh"
product_load_env

GATEWAY="$(product_gateway_url)"
DEMO_PASSWORD="${DEMO_PASSWORD:-chanter-dev-demo}"
OWNER_EMAIL="dev-demo-owner@chanter.local"
KEEP_NAME="Workable Product Demo"

login() {
  curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$OWNER_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}"
}

psql_community() {
  PGPASSWORD="${POSTGRES_PASSWORD:-chanter}" psql -h "${POSTGRES_HOST:-localhost}" -p "${POSTGRES_PORT:-5432}" \
    -U "${POSTGRES_USER:-chanter}" -d chanter_community -At "$@"
}

echo "==> Resolve demo owner"
OWNER_JSON=$(login)
OWNER_ID=$(echo "$OWNER_JSON" | python3 -c "import sys,json; print(json.load(sys.stdin)['user']['id'])")

KEEP_ID=$(psql_community -c "
SELECT id::text
FROM study_servers
WHERE owner_user_id = '$OWNER_ID' AND name = '$KEEP_NAME'
ORDER BY created_at DESC
LIMIT 1;
" | tr -d '[:space:]')

if [[ -z "$KEEP_ID" ]]; then
  echo "No '$KEEP_NAME' server found for demo owner."
  KEEP_ID="__none__"
fi

TO_DELETE=$(psql_community -c "
SELECT id::text
FROM study_servers
WHERE owner_user_id = '$OWNER_ID'
  AND id::text <> '$KEEP_ID'
ORDER BY created_at;
")

if [[ -z "$TO_DELETE" ]]; then
  echo "No duplicate study servers to remove."
  exit 0
fi

echo "==> Deleting duplicate study servers (keeping ${KEEP_ID})"
while IFS= read -r server_id; do
  [ -n "$server_id" ] || continue
  name=$(psql_community -c "SELECT name FROM study_servers WHERE id = '$server_id';" | tr -d '[:space:]')
  echo "   delete: $name ($server_id)"
  psql_community -c "DELETE FROM study_servers WHERE id = '$server_id';" >/dev/null
done <<<"$TO_DELETE"

count=$(echo "$TO_DELETE" | grep -c . || true)
echo "OK: removed $count duplicate study server(s)."
