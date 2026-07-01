#!/usr/bin/env bash
# Seed FAQ + course resource and verify global search (issue #57).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
source .env 2>/dev/null || true

GATEWAY="${GATEWAY:-http://localhost:8080}"
DEMO_PASSWORD="chanter-dev-demo"
MARKER="issue57-search-demo-$(date +%s)"

login() {
  local email="$1"
  curl -sf -X POST "$GATEWAY/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$DEMO_PASSWORD\"}" \
    || curl -sf -X POST "$GATEWAY/api/v1/auth/register" \
      -H 'Content-Type: application/json' \
      -d "{\"email\":\"$email\",\"password\":\"$DEMO_PASSWORD\",\"displayName\":\"$2\"}"
}

json_field() {
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d$1)"
}

echo "==> Logging in demo personas"
OWNER_JSON=$(login "dev-demo-owner@chanter.local" "Demo Owner")
LEARNER_JSON=$(login "dev-demo-learner@chanter.local" "Demo Learner")
OWNER_TOKEN=$(echo "$OWNER_JSON" | json_field "['accessToken']")
LEARNER_TOKEN=$(echo "$LEARNER_JSON" | json_field "['accessToken']")
OWNER_ID=$(echo "$OWNER_JSON" | json_field "['user']['id']")
LEARNER_ID=$(echo "$LEARNER_JSON" | json_field "['user']['id']")

echo "==> Resolve or create Study Server"
SERVERS=$(curl -sf "$GATEWAY/api/v1/study-servers" -H "Authorization: Bearer $OWNER_TOKEN")
SERVER_ID=$(echo "$SERVERS" | python3 -c "import sys,json; s=json.load(sys.stdin); print(s[0]['id'] if s else '')" 2>/dev/null || true)
if [[ -z "$SERVER_ID" ]]; then
  CREATED=$(curl -sf -X POST "$GATEWAY/api/v1/study-servers" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"$MARKER Study Server\"}")
  SERVER_ID=$(echo "$CREATED" | json_field "['id']")
fi

NAV=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/navigation" -H "Authorization: Bearer $OWNER_TOKEN")
COURSE_ID=$(echo "$NAV" | python3 -c "import sys,json; d=json.load(sys.stdin); cs=d.get('courses') or []; print(cs[0]['id'] if cs else '')")
if [[ -z "$COURSE_ID" ]]; then
  COURSE=$(curl -sf -X POST "$GATEWAY/api/v1/study-servers/$SERVER_ID/courses" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"title\":\"$MARKER Course\",\"cohortName\":\"$MARKER Cohort\"}")
  COURSE_ID=$(echo "$COURSE" | json_field "['id']")
  NAV=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/navigation" -H "Authorization: Bearer $OWNER_TOKEN")
fi

QUESTIONS_CHANNEL_ID=$(echo "$NAV" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for c in d.get('courses', []):
  if c['id'] == '$COURSE_ID':
    for ch in c.get('channels', []):
      if ch.get('name') == 'questions':
        print(ch['id']); raise SystemExit
")

echo "   server=$SERVER_ID course=$COURSE_ID questions=$QUESTIONS_CHANNEL_ID"

echo "==> Enroll learner (idempotent)"
COHORT_ID=$(echo "$NAV" | python3 -c "
import sys,json
d=json.load(sys.stdin)
for c in d.get('courses', []):
  if c['id'] == '$COURSE_ID' and c.get('cohorts'):
    print(c['cohorts'][0]['id']); raise SystemExit
")
curl -sf -X POST "$GATEWAY/api/v1/cohorts/$COHORT_ID/enrollments" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"learnerUserId\":\"$LEARNER_ID\"}" >/dev/null || true

echo "==> Post support question (learner)"
SQ1=$(curl -sf -X POST "$GATEWAY/api/v1/course-channels/$QUESTIONS_CHANNEL_ID/support-questions" \
  -H "Authorization: Bearer $LEARNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"body\":\"Where is homework help for $MARKER?\",\"idempotencyKey\":\"$MARKER-sq1\"}")
SQ1_ID=$(echo "$SQ1" | json_field "['id']")

echo "==> Approve FAQ (owner)"
curl -sf -X POST "$GATEWAY/api/v1/courses/$COURSE_ID/approved-faqs" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{
    \"channelId\":\"$QUESTIONS_CHANNEL_ID\",
    \"approvedByUserId\":\"$OWNER_ID\",
    \"question\":\"How do I find homework help for $MARKER?\",
    \"answer\":\"Use global search (Cmd+K) to find approved FAQs and resources.\",
    \"sourceSupportQuestionIds\":[\"$SQ1_ID\"]
  }" >/dev/null

echo "==> Upload course resource (owner)"
RESOURCE_FILE="$ROOT/scripts/.issue-57-demo-resource.txt"
printf 'Demo resource body for %s\n' "$MARKER" >"$RESOURCE_FILE"
curl -sf -X POST "$GATEWAY/api/v1/courses/$COURSE_ID/course-resources" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  -F "title=$MARKER Lecture Slides" \
  -F "aiApproved=true" \
  -F "file=@$RESOURCE_FILE;type=text/plain" >/dev/null

echo "==> Reindex search"
REINDEX=$(curl -sf -X POST "$GATEWAY/api/v1/study-servers/$SERVER_ID/search/reindex" \
  -H "Authorization: Bearer $OWNER_TOKEN")
INDEXED=$(echo "$REINDEX" | json_field "['indexedDocuments']")
echo "   indexedDocuments=$INDEXED"

echo "==> Search as owner (query: homework)"
OWNER_HITS=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/search?q=homework" \
  -H "Authorization: Bearer $OWNER_TOKEN")
OWNER_COUNT=$(echo "$OWNER_HITS" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('results',[])))")
echo "   ownerHits=$OWNER_COUNT"
echo "$OWNER_HITS" | python3 -m json.tool

echo "==> Search as learner (query: lecture)"
LEARNER_HITS=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/search?q=lecture" \
  -H "Authorization: Bearer $LEARNER_TOKEN")
LEARNER_COUNT=$(echo "$LEARNER_HITS" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('results',[])))")
echo "   learnerHits=$LEARNER_COUNT"

echo "==> Search as stranger (query: homework) — expect 0 hits or 403"
STRANGER_JSON=$(login "dev-demo-nonEnrolled@chanter.local" "Demo Stranger")
STRANGER_TOKEN=$(echo "$STRANGER_JSON" | json_field "['accessToken']")
STRANGER_HTTP=$(curl -s -o /tmp/issue57-stranger-search.json -w "%{http_code}" \
  "$GATEWAY/api/v1/study-servers/$SERVER_ID/search?q=homework" \
  -H "Authorization: Bearer $STRANGER_TOKEN")
if [[ "$STRANGER_HTTP" == "403" ]]; then
  echo "   strangerBlocked=403 (no study-server access)"
  STRANGER_OK=1
elif [[ "$STRANGER_HTTP" == "200" ]]; then
  STRANGER_COUNT=$(python3 -c "import json; print(len(json.load(open('/tmp/issue57-stranger-search.json')).get('results',[])))")
  echo "   strangerHits=$STRANGER_COUNT"
  STRANGER_OK=$([[ "$STRANGER_COUNT" == "0" ]] && echo 1 || echo 0)
else
  echo "   strangerHttp=$STRANGER_HTTP (unexpected)" >&2
  STRANGER_OK=0
fi

if [[ "$INDEXED" -lt 2 || "$OWNER_COUNT" -lt 1 || "$LEARNER_COUNT" -lt 1 || "$STRANGER_OK" != "1" ]]; then
  echo "FAILED: expected indexed>=2, owner>=1, learner>=1, stranger blocked or 0 hits" >&2
  exit 1
fi

echo "OK: global search end-to-end verified"
echo "SERVER_ID=$SERVER_ID"
echo "Open: http://127.0.0.1:5173/app/servers/$SERVER_ID/home"
