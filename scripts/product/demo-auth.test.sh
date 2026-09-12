#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT/scripts/product/demo-auth.sh"
GATEWAY=http://localhost:8080
FRONTEND=http://localhost:5173
export DEMO_PASSWORD=local-test-password
calls=$(mktemp)
verified=$(mktemp)
trap 'rm -f "$calls" "$verified"' EXIT
printf '0' > "$calls"
scenario=verified
python3() { printf '{}'; }
node() { printf 'unexpected-verification' > "$verified"; }
curl() {
  local call
  call=$(cat "$calls")
  call=$((call + 1))
  printf '%s' "$call" > "$calls"
  case "$scenario:$call" in
    *:1) printf '{}\n503' ;;
    *:2) printf '{"message":"Check your inbox"}\n202' ;;
    verified:3|unverified:4) printf '{"accessToken":"test-bearer"}\n200' ;;
    unverified:3) printf '{}\n403' ;;
    wrong-password:3) printf '{}\n401' ;;
    *) echo 'Unexpected auth request' >&2; return 1 ;;
  esac
}
result=$(login dev-demo-owner@chanter.local 'Demo Owner')
test "$result" = '{"accessToken":"test-bearer"}'
test ! -s "$verified" || { echo 'FAIL: existing verified account incorrectly required a new verification link' >&2; exit 1; }
echo 'ok: neutral registration retries login before looking for verification mail'

scenario=unverified
printf '0' > "$calls"
result=$(login dev-demo-owner@chanter.local 'Demo Owner')
test "$result" = '{"accessToken":"test-bearer"}'
test -s "$verified"
echo 'ok: a login requiring verification consumes the local mail then signs in'

scenario=wrong-password
printf '0' > "$calls"
printf '' > "$verified"
if result=$(login dev-demo-owner@chanter.local 'Demo Owner' 2>/dev/null); then
  echo 'FAIL: existing-account password mismatch was accepted' >&2; exit 1
fi
test ! -s "$verified"
echo 'ok: wrong password fails without waiting for nonexistent verification mail'
