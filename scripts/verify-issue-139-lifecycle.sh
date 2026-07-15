#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/product/lib.sh
product_load_env
DEMO_PASSWORD="${DEMO_PASSWORD:-chanter-dev-demo}"
GATEWAY="$(product_gateway_url)"
TOKEN=$(curl -sf -m 10 -X POST "$GATEWAY/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"dev-demo-owner@chanter.local\",\"password\":\"${DEMO_PASSWORD}\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
SERVER=$(curl -sf -H "Authorization: Bearer $TOKEN" "$GATEWAY/api/v1/study-servers" \
  | python3 -c 'import sys,json; s=json.load(sys.stdin); print(s[0]["id"])')
COURSE=$(curl -sf -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  "$GATEWAY/api/v1/study-servers/$SERVER/courses" \
  -d '{"title":"TEST 139 Draft","description":"Lifecycle proof"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
echo "created course $COURSE"
LIFECYCLE=$(curl -s -w '\nHTTP:%{http_code}' -H "Authorization: Bearer $TOKEN" "$GATEWAY/api/v1/courses/$COURSE")
echo "$LIFECYCLE" | tail -3
BODY=$(echo "$LIFECYCLE" | sed '$d')
echo "$BODY" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["published"] is False; assert d["description"]=="Lifecycle proof"; print("draft ok")'
curl -sf -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  "$GATEWAY/api/v1/courses/$COURSE/cohorts" -d '{"name":"Proof cohort"}' >/dev/null
curl -sf -X POST -H "Authorization: Bearer $TOKEN" "$GATEWAY/api/v1/courses/$COURSE/publish" -o /dev/null
echo "lifecycle-api-ok course=$COURSE server=$SERVER"
