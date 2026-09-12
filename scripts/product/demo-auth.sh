#!/usr/bin/env bash
require_http() {
  local label="$1"
  local code="$2"
  local body="$3"
  if [[ "$code" -lt 200 || "$code" -ge 300 ]]; then
    echo "ERROR: $label failed (HTTP $code)" >&2
    if [[ -n "$body" ]]; then
      echo "$body" | head -c 500 >&2
      echo >&2
    fi
    echo "Hint: run make product-up && make product-health first" >&2
    exit 1
  fi
}

curl_json() {
  local label="$1"
  shift
  local response code body
  response=$(curl -sS -w $'\n%{http_code}' "$@")
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  require_http "$label" "$code" "$body"
  echo "$body"
}

login() {
  local email="$1"
  local display_name="${2:-}"
  local body code response login_payload register_payload
  login_payload=$(python3 -c 'import json, os, sys; print(json.dumps({"email": sys.argv[1], "password": os.environ["DEMO_PASSWORD"]}))' "$email")
  response=$(curl -sS -w $'\n%{http_code}' -X POST "$GATEWAY/api/v1/auth/login" \
    -H "Origin: ${CHANTER_PUBLIC_BASE_URL:-$FRONTEND}" -H 'X-Chanter-CSRF: 1' \
    -H 'Content-Type: application/json' \
    -d "$login_payload")
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [[ "$code" -ge 200 && "$code" -lt 300 ]]; then
    echo "$body"
    return 0
  fi
  if [[ "$code" != '401' && "$code" != '403' ]]; then
    require_http "demo login $email" "$code" "$body"
  fi
  register_payload=$(python3 -c 'import json, os, sys; print(json.dumps({"email": sys.argv[1], "password": os.environ["DEMO_PASSWORD"], "displayName": sys.argv[2]}))' "$email" "$display_name")
  response=$(curl -sS -w $'\n%{http_code}' -X POST "$GATEWAY/api/v1/auth/register" \
    -H "Origin: ${CHANTER_PUBLIC_BASE_URL:-$FRONTEND}" -H 'X-Chanter-CSRF: 1' \
    -H 'Content-Type: application/json' \
    -d "$register_payload")
  code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  require_http "login/register $email" "$code" "$body"
  if [[ "$code" == "202" ]]; then
    # Registration is intentionally neutral for existing verified accounts too.
    response=$(curl -sS -w $'\n%{http_code}' -X POST "$GATEWAY/api/v1/auth/login" \
      -H "Origin: ${CHANTER_PUBLIC_BASE_URL:-$FRONTEND}" -H 'X-Chanter-CSRF: 1' \
      -H 'Content-Type: application/json' -d "$login_payload")
    code="${response##*$'\n'}"
    body="${response%$'\n'*}"
    if [[ "$code" == "403" ]]; then
      node "$ROOT/scripts/product/verify-demo-email.mjs" "$email"
      body=$(curl_json "login verified demo persona" -X POST "$GATEWAY/api/v1/auth/login" \
        -H "Origin: ${CHANTER_PUBLIC_BASE_URL:-$FRONTEND}" -H 'X-Chanter-CSRF: 1' \
        -H 'Content-Type: application/json' -d "$login_payload")
    else
      require_http 'demo login after neutral registration (check DEMO_PASSWORD for existing accounts)' "$code" "$body"
    fi
  fi
  echo "$body"
}

