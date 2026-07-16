#!/usr/bin/env bash
# Create or refresh local .env with unique JWT / internal-service secrets (SEC-04).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/product/lib.sh
source "$SCRIPT_DIR/lib.sh"

root="$(product_repo_root)"
env_file="${CHANTER_PRODUCT_ENV_FILE:-$root/.env}"
example="$root/.env.example"

if [ ! -f "$example" ]; then
  echo "Missing $example" >&2
  exit 1
fi

if [ ! -f "$env_file" ]; then
  cp "$example" "$env_file"
  echo "Created $env_file from .env.example"
fi

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '\n'
    return 0
  fi
  # Fallback when openssl is unavailable (rare on developer machines).
  python3 - <<'PY'
import secrets, base64
print(base64.b64encode(secrets.token_bytes(48)).decode("ascii"))
PY
}

upsert_secret() {
  local key="$1"
  local value="$2"
  local file="$3"
  if grep -q "^${key}=" "$file" 2>/dev/null; then
    # Portable in-place replace without relying on GNU sed -i.
    local tmp
    tmp="$(mktemp)"
    awk -v k="$key" -v v="$value" '
      BEGIN { done=0 }
      index($0, k "=") == 1 {
        print k "=" v
        done=1
        next
      }
      { print }
      END { if (!done) print k "=" v }
    ' "$file" > "$tmp"
    mv "$tmp" "$file"
  else
    printf '\n%s=%s\n' "$key" "$value" >> "$file"
  fi
}

needs_replace() {
  local key="$1"
  local forbidden="$2"
  local current
  current="$(grep -E "^${key}=" "$env_file" | head -1 | cut -d= -f2- || true)"
  if [ -z "$current" ] || [ "${#current}" -lt 32 ] || [ "$current" = "$forbidden" ]; then
    return 0
  fi
  return 1
}

changed=0
if needs_replace "CHANTER_JWT_SECRET" "$CHANTER_FORBIDDEN_JWT_SECRET_DEFAULT"; then
  upsert_secret "CHANTER_JWT_SECRET" "$(random_secret)" "$env_file"
  echo "Set CHANTER_JWT_SECRET to a new random value"
  changed=1
fi
if needs_replace "CHANTER_INTERNAL_SERVICE_TOKEN" "$CHANTER_FORBIDDEN_INTERNAL_SERVICE_TOKEN_DEFAULT"; then
  upsert_secret "CHANTER_INTERNAL_SERVICE_TOKEN" "$(random_secret)" "$env_file"
  echo "Set CHANTER_INTERNAL_SERVICE_TOKEN to a new random value"
  changed=1
fi

if ! grep -q '^DEMO_PASSWORD=' "$env_file" 2>/dev/null; then
  printf '\nDEMO_PASSWORD=chanter-dev-demo\n' >> "$env_file"
  echo "Added DEMO_PASSWORD=chanter-dev-demo (local demo seed)"
  changed=1
fi

if [ "$changed" -eq 0 ]; then
  echo "$env_file already has unique runtime secrets (≥32 chars, not known defaults)."
else
  echo "Ready. Next: make product-up   # or make product-supervise"
fi
