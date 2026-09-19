#!/usr/bin/env bash
# Seed study server, enrollment, and friendship for the workable product demo (#63).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck source=scripts/product/lib.sh
source "$ROOT/scripts/product/lib.sh"
product_load_env

GATEWAY="${GATEWAY:-$(product_gateway_url)}"
FRONTEND="${FRONTEND:-http://localhost:${FRONTEND_PORT:-5173}}"
if [[ -z "${DEMO_PASSWORD:-}" ]]; then
  echo "ERROR: DEMO_PASSWORD is not set." >&2
  echo "Add to .env:  echo 'DEMO_PASSWORD=chanter-dev-demo' >> .env" >&2
  echo "Or run:       make product-demo-seed  (Makefile supplies a local default)" >&2
  exit 1
fi
OWNER_EMAIL="dev-demo-owner@chanter.local"
MEMBER_EMAIL="dev-demo-member@chanter.local"
LEARNER_EMAIL="dev-demo-learner@chanter.local"
DEMO_SERVER_NAME="Workable Product Demo"

# shellcheck source=scripts/product/demo-auth.sh
source "$ROOT/scripts/product/demo-auth.sh"

json_field() {
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d$1)"
}

echo "==> Logging in demo personas (password from DEMO_PASSWORD)"
OWNER_JSON=$(login "$OWNER_EMAIL" "Demo Owner")
MEMBER_JSON=$(login "$MEMBER_EMAIL" "Demo Member")
LEARNER_JSON=$(login "$LEARNER_EMAIL" "Demo Learner")
OWNER_TOKEN=$(echo "$OWNER_JSON" | json_field "['accessToken']")
MEMBER_TOKEN=$(echo "$MEMBER_JSON" | json_field "['accessToken']")
LEARNER_TOKEN=$(echo "$LEARNER_JSON" | json_field "['accessToken']")
OWNER_ID=$(echo "$OWNER_JSON" | json_field "['user']['id']")
LEARNER_ID=$(echo "$LEARNER_JSON" | json_field "['user']['id']")

echo "==> Resolve or create Study Server"
SERVERS=$(curl -sf "$GATEWAY/api/v1/study-servers" -H "Authorization: Bearer $OWNER_TOKEN")
SERVER_ID=$(echo "$SERVERS" | python3 -c "
import sys, json
servers = json.load(sys.stdin)
for server in servers:
  if server.get('name') == '$DEMO_SERVER_NAME':
    print(server['id']); raise SystemExit
print('')
" 2>/dev/null || true)
if [[ -z "$SERVER_ID" ]]; then
  CREATED=$(curl -sf -X POST "$GATEWAY/api/v1/study-servers" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"$DEMO_SERVER_NAME\"}")
  SERVER_ID=$(echo "$CREATED" | json_field "['id']")
fi

echo "==> Ensure membership-only persona (idempotent)"
MEMBER_SERVERS=$(curl -sf "$GATEWAY/api/v1/study-servers" -H "Authorization: Bearer $MEMBER_TOKEN")
MEMBER_HAS_SERVER=$(echo "$MEMBER_SERVERS" | python3 -c "
import sys, json
servers = json.load(sys.stdin)
print('yes' if any(server.get('id') == '$SERVER_ID' for server in servers) else 'no')
")
if [[ "$MEMBER_HAS_SERVER" == "yes" ]]; then
  echo "   membership already accepted"
else
  MEMBER_INVITATIONS=$(curl -sf "$GATEWAY/api/v1/study-server-invitations" \
    -H "Authorization: Bearer $MEMBER_TOKEN")
  MEMBER_INVITATION_ID=$(echo "$MEMBER_INVITATIONS" | python3 -c "
import sys, json
invitations = json.load(sys.stdin)
for invitation in invitations:
  if invitation.get('studyServerId') == '$SERVER_ID':
    print(invitation['id']); raise SystemExit
print('')
")
  if [[ -z "$MEMBER_INVITATION_ID" ]]; then
    CREATED_INVITATIONS=$(curl_json "invite membership-only persona" \
      -X POST "$GATEWAY/api/v1/study-servers/$SERVER_ID/invitations" \
      -H "Authorization: Bearer $OWNER_TOKEN" \
      -H 'Content-Type: application/json' \
      -d "{\"inviteEmails\":[\"$MEMBER_EMAIL\"]}")
    MEMBER_INVITATION_ID=$(echo "$CREATED_INVITATIONS" | python3 -c \
      "import sys,json; invitations=json.load(sys.stdin); print(invitations[0]['id'])")
  fi
  curl_json "accept membership-only invitation" \
    -X POST "$GATEWAY/api/v1/study-servers/$SERVER_ID/invitations/$MEMBER_INVITATION_ID/accept" \
    -H "Authorization: Bearer $MEMBER_TOKEN" >/dev/null
  echo "   membership accepted without Cohort enrollment"
fi

NAV=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/navigation" -H "Authorization: Bearer $OWNER_TOKEN")
COURSE_ID=$(echo "$NAV" | python3 -c "import sys,json; d=json.load(sys.stdin); cs=d.get('courses') or []; print(cs[0]['id'] if cs else '')")
if [[ -z "$COURSE_ID" ]]; then
  COURSE=$(curl -sf -X POST "$GATEWAY/api/v1/study-servers/$SERVER_ID/courses" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"title":"Demo Course","cohortName":"Demo Cohort"}')
  COURSE_ID=$(echo "$COURSE" | json_field "['id']")
  NAV=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/navigation" -H "Authorization: Bearer $OWNER_TOKEN")
fi

CHANNEL_IDS=$(echo "$NAV" | python3 -c "
import sys, json
d = json.load(sys.stdin)
course_id = '$COURSE_ID'
announcements = ''
general = ''
study_room = ''
questions = ''
for c in d.get('courses', []):
  if c['id'] == course_id:
    for ch in c.get('channels', []):
      if ch.get('name') == 'announcements':
        announcements = ch['id']
      if ch.get('name') == 'questions':
        questions = ch['id']
for ch in d.get('studyServerChannels', []):
  if ch.get('name') == 'general':
    general = ch['id']
  if ch.get('name') == 'study-room':
    study_room = ch['id']
print(announcements, general, study_room, questions)
")
read -r ANNOUNCEMENTS_CHANNEL_ID GENERAL_CHANNEL_ID STUDY_ROOM_CHANNEL_ID QUESTIONS_CHANNEL_ID <<<"$CHANNEL_IDS"

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

echo "==> Ensure friendship (owner → learner)"
FRIEND_STATUS=$(curl -sf "$GATEWAY/api/v1/friendships/status?peerUserId=$LEARNER_ID" \
  -H "Authorization: Bearer $OWNER_TOKEN")
STATUS=$(echo "$FRIEND_STATUS" | json_field "['status']")
if [[ "$STATUS" == "NONE" ]]; then
  REQUEST=$(curl -sf -X POST "$GATEWAY/api/v1/friend-requests" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"recipientUserId\":\"$LEARNER_ID\"}")
  REQUEST_ID=$(echo "$REQUEST" | json_field "['id']")
  curl -sf -X POST "$GATEWAY/api/v1/friend-requests/$REQUEST_ID/acceptance" \
    -H "Authorization: Bearer $LEARNER_TOKEN" >/dev/null
  echo "   friendship created"
elif [[ "$STATUS" == "ACCEPTED" ]]; then
  echo "   friendship already accepted"
else
  echo "   friendship status=$STATUS (complete pending request in UI or remove and re-run)"
fi

echo "==> Upload AI-approved course resource for Study Assistant grounding"
RESOURCE_TITLE="Homework Help Guide"
EXISTING_RESOURCES=$(curl -sf "$GATEWAY/api/v1/courses/$COURSE_ID/course-resources" \
  -H "Authorization: Bearer $OWNER_TOKEN")
RESOURCE_ID=$(echo "$EXISTING_RESOURCES" | python3 -c "
import sys, json
data = json.load(sys.stdin)
resources = data.get('courseResources', data.get('resources', []))
print(next((r['id'] for r in resources if r.get('title') == '$RESOURCE_TITLE'
            and r.get('status') in ('PROCESSING', 'AVAILABLE') and r.get('aiApproved') is True), ''))
")
if [[ -n "$RESOURCE_ID" ]]; then
  echo "   reusing existing $RESOURCE_TITLE"
else
  RESOURCE_FILE="$ROOT/scripts/.workable-demo-ai-resource.txt"
  cat >"$RESOURCE_FILE" <<'EOF'
Homework help for the Workable Product Demo course:

Submit homework assignments through the course portal before Friday at 11:59 PM.
Late submissions receive a ten percent penalty per day unless you request an extension
from your instructor in the questions channel.
EOF
  RESOURCE_JSON=$(curl -sf -X POST "$GATEWAY/api/v1/courses/$COURSE_ID/course-resources" \
    -H "Authorization: Bearer $OWNER_TOKEN" \
    -F "title=$RESOURCE_TITLE" \
    -F "aiApproved=true" \
    -F "file=@$RESOURCE_FILE;type=text/plain;filename=homework-help-guide.txt")
  RESOURCE_ID=$(echo "$RESOURCE_JSON" | json_field "['id']")
  echo "   queued $RESOURCE_TITLE for validation and scanning"
fi

echo "==> Wait for a clean resource and usable Study Assistant index"
RESOURCE_READY=false
RESOURCE_DEADLINE=$((SECONDS + 180))
while ((SECONDS < RESOURCE_DEADLINE)); do
  RESOURCE_METADATA=$(curl -sf --max-time 10 "$GATEWAY/api/v1/course-resources/$RESOURCE_ID" \
    -H "Authorization: Bearer $OWNER_TOKEN")
  RESOURCE_STATE=$(echo "$RESOURCE_METADATA" | json_field "['status']")
  RESOURCE_PREPARATION=$(echo "$RESOURCE_METADATA" | json_field ".get('ingestionStatus', '')")
  if [[ "$RESOURCE_STATE" == "REJECTED" || "$RESOURCE_STATE" == "FAILED" ]]; then
    echo "error: demo resource did not pass scanning ($RESOURCE_STATE)" >&2
    exit 1
  fi
  if [[ "$RESOURCE_STATE" == "AVAILABLE" ]]; then
    case "$RESOURCE_PREPARATION" in
      FAILED|EMPTY|OCR_REQUIRED|ENCRYPTED|MALFORMED|UNSUPPORTED|LIMIT_EXCEEDED|NONE)
        echo "error: demo resource cannot provide Study Assistant evidence ($RESOURCE_PREPARATION)" >&2
        exit 1
        ;;
    esac
  fi
  if [[ "$RESOURCE_STATE" == "AVAILABLE" && "$RESOURCE_PREPARATION" == "READY" ]]; then
    if CHUNK_COUNT=$(curl -sf --max-time 10 \
      "${AGENT_SERVICE_URL:-http://localhost:${AGENT_PORT:-8085}}/api/v1/internal/resource-chunks/$RESOURCE_ID" \
      -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN" \
      | python3 -c 'import sys,json; print(len(json.load(sys.stdin)["chunks"]))' 2>/dev/null) && [[ "$CHUNK_COUNT" -gt 0 ]]; then
      RESOURCE_READY=true
      break
    fi
  fi
  sleep 3
done
if [[ "$RESOURCE_READY" != "true" ]]; then
  echo "error: demo resource scan/index did not become ready within the bounded wait" >&2
  exit 1
fi
echo "   resource is available and indexed"

echo "==> Install AI Study Assistant (idempotent)"
ASSISTANT_INSTALLED=$(curl -sf "$GATEWAY/api/v1/study-servers/$SERVER_ID/study-assistant" \
  -H "Authorization: Bearer $OWNER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('installed', False))")
if [[ "$ASSISTANT_INSTALLED" == "True" ]]; then
  echo "   already installed (re-run on a fresh stack to pick up new resource grants)"
else
  PREVIEW=$(curl_json "study-assistant install-preview" \
    "$GATEWAY/api/v1/study-servers/$SERVER_ID/study-assistant/install-preview" \
    -H "Authorization: Bearer $OWNER_TOKEN")
  INSTALL_BODY=$(echo "$PREVIEW" | python3 -c "
import sys, json
preview = json.load(sys.stdin)
if preview.get('alreadyInstalled'):
    raise SystemExit(0)
if not any(resource['id'] == '$RESOURCE_ID' for resource in preview.get('courseResources', [])):
    raise SystemExit('Ready demo resource is missing from the authorized install preview')
grants = []
for ch in preview['candidates']['studyServerChannels']:
    grants.append({'grantType': 'STUDY_SERVER_CHANNEL', 'grantTargetId': ch['id']})
for course in preview['candidates']['courses']:
    grants.append({'grantType': 'COURSE', 'grantTargetId': course['id']})
    for cohort in course.get('cohorts', []):
        grants.append({'grantType': 'COHORT', 'grantTargetId': cohort['id']})
    for ch in course.get('channels', []):
        grants.append({'grantType': 'COURSE_CHANNEL', 'grantTargetId': ch['id']})
for res in preview.get('courseResources', []):
    grants.append({'grantType': 'COURSE_RESOURCE', 'grantTargetId': res['id']})
print(json.dumps({'grants': grants}))
")
  if [[ -n "$INSTALL_BODY" ]]; then
    curl -sf -X POST "$GATEWAY/api/v1/study-servers/$SERVER_ID/study-assistant/install" \
      -H "Authorization: Bearer $OWNER_TOKEN" \
      -H 'Content-Type: application/json' \
      -d "$INSTALL_BODY" >/dev/null
    echo "   installed with channel + resource grants"
  fi
fi

echo ""
echo "OK: workable product demo data is ready"
echo ""
echo "Demo personas (password: $DEMO_PASSWORD)"
echo "  Owner:   $OWNER_EMAIL"
echo "  Member:  $MEMBER_EMAIL"
echo "  Learner: $LEARNER_EMAIL"
echo ""
echo "Open in two browser profiles:"
echo "  Sign in:     $FRONTEND/sign-in"
echo "  Server home: $FRONTEND/app/servers/$SERVER_ID/home"
echo "  #announcements (course text chat): $FRONTEND/app/servers/$SERVER_ID/course-channels/$ANNOUNCEMENTS_CHANNEL_ID"
echo "  #questions (AI Study Assistant):   $FRONTEND/app/servers/$SERVER_ID/course-channels/$QUESTIONS_CHANNEL_ID"
echo "  #general (study text chat):        $FRONTEND/app/servers/$SERVER_ID/study-channels/$GENERAL_CHANNEL_ID"
echo "  study-room (voice):                $FRONTEND/app/servers/$SERVER_ID/study-channels/$STUDY_ROOM_CHANNEL_ID"
echo "  Friends Hub (DM + optional call):  $FRONTEND/app/friends"
echo ""
echo "Try AI (as learner in #questions):"
echo "  Ask: How do I submit homework before the deadline?"
echo "  Then click Ask AI — see docs/operations/ai-study-assistant.md for how answers work."
echo ""
echo "SERVER_ID=$SERVER_ID"
echo "COURSE_ID=$COURSE_ID"
