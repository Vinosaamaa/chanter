#!/usr/bin/env bash
# Create or refresh local .env with unique secrets (SEC-04, SEC-12).
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

random_alphanumeric() {
  local length="${1:-32}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$length"
    return 0
  fi
  python3 - "$length" <<'PY'
import secrets, string, sys
length = int(sys.argv[1])
alphabet = string.ascii_letters + string.digits
print(''.join(secrets.choice(alphabet) for _ in range(length)))
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
  local current
  current="$(grep -E "^${key}=" "$env_file" | head -1 | cut -d= -f2- || true)"
  if [ -z "$current" ]; then
    return 0
  fi
  return 1
}

needs_replace_secret() {
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

# JWT and internal service token (SEC-04)
if needs_replace_secret "CHANTER_JWT_SECRET" "$CHANTER_FORBIDDEN_JWT_SECRET_DEFAULT"; then
  upsert_secret "CHANTER_JWT_SECRET" "$(random_secret)" "$env_file"
  echo "Set CHANTER_JWT_SECRET to a new random value"
  changed=1
fi
if needs_replace_secret "CHANTER_INTERNAL_SERVICE_TOKEN" "$CHANTER_FORBIDDEN_INTERNAL_SERVICE_TOKEN_DEFAULT"; then
  upsert_secret "CHANTER_INTERNAL_SERVICE_TOKEN" "$(random_secret)" "$env_file"
  echo "Set CHANTER_INTERNAL_SERVICE_TOKEN to a new random value"
  changed=1
fi

# Redis password (SEC-12) — empty in .env.example; generate a random value here.
if needs_replace "REDIS_PASSWORD"; then
  upsert_secret "REDIS_PASSWORD" "$(random_alphanumeric 32)" "$env_file"
  echo "Set REDIS_PASSWORD to a new random value"
  changed=1
fi

# MinIO credentials (SEC-12) — empty in .env.example; set local defaults here.
if needs_replace "MINIO_ROOT_USER"; then
  upsert_secret "MINIO_ROOT_USER" "chanter-local" "$env_file"
  echo "Set MINIO_ROOT_USER to chanter-local"
  changed=1
fi
if needs_replace "MINIO_ROOT_PASSWORD"; then
  upsert_secret "MINIO_ROOT_PASSWORD" "$(random_alphanumeric 32)" "$env_file"
  echo "Set MINIO_ROOT_PASSWORD to a new random value"
  changed=1
fi

# LiveKit API key/secret (SEC-12) — empty in .env.example; generate here.
if needs_replace "LIVEKIT_API_KEY"; then
  upsert_secret "LIVEKIT_API_KEY" "chanterlocal" "$env_file"
  echo "Set LIVEKIT_API_KEY to chanterlocal"
  changed=1
fi
if needs_replace "LIVEKIT_API_SECRET"; then
  upsert_secret "LIVEKIT_API_SECRET" "$(random_alphanumeric 48)" "$env_file"
  echo "Set LIVEKIT_API_SECRET to a new random value"
  changed=1
fi

if ! grep -q '^DEMO_PASSWORD=' "$env_file" 2>/dev/null; then
  printf '\nDEMO_PASSWORD=chanter-dev-demo\n' >> "$env_file"
  echo "Added DEMO_PASSWORD=chanter-dev-demo (local demo seed)"
  changed=1
fi

if [ "$changed" -eq 0 ]; then
  echo "$env_file already has unique runtime secrets (no empty placeholders, not known defaults)."
else
  echo "Ready. Next: make product-up   # or make product-supervise"
fi
