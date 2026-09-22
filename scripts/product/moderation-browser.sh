#!/usr/bin/env bash
# Hosted-only combined moderation proof. Never starts a local developer preview.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_OS:-}" == Linux ]] || { echo 'Moderation browser proof requires the hosted Linux job.' >&2; exit 1; }
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
source scripts/product/lib.sh
state="$ROOT/.product/moderation-browser"
mkdir -p "$state" .product/pids .product/logs

case "${1:-}" in
prepare)
  python3 - <<'PY'
import base64, secrets
with open('.env','a',encoding='utf-8') as file:
    file.write('\nCHANTER_OPERATOR_ENCRYPTION_KEY='+base64.b64encode(secrets.token_bytes(32)).decode()+'\n')
    file.write('CHANTER_PUBLIC_BASE_URL=https://127.0.0.1:9420\n')
    file.write('CHANTER_CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173,http://127.0.0.1:9419,https://127.0.0.1:9420\n')
    file.write('LIVEKIT_URL=ws://127.0.0.1:9419/livekit\n')
PY
  (cd frontend && npm run build && node ../scripts/product/build-moderation-audio.mjs)
  curl --fail --location --retry 2 --max-time 120 https://github.com/caddyserver/caddy/releases/download/v2.11.4/caddy_2.11.4_linux_amd64.tar.gz -o "$state/caddy.tar.gz"
  echo "8220d1f013b6f27510247b2360c9e0ca9f018feebd82515f07635318b34ff9777ccc8fd0b6e6f2486ce3a33fe389fbb7db12d05baa474f4587509fb4f5ebf1c9  $state/caddy.tar.gz" | sha512sum --check
  tar -xzf "$state/caddy.tar.gz" -C "$state" caddy
  # Ephemeral trust belongs only to this disposable hosted job. Keep browser
  # certificate validation enabled, and never publish this private key.
  openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
    -keyout "$state/ca.key" -out "$state/ca.crt" -subj '/CN=Chanter hosted browser fixture CA' \
    -addext 'basicConstraints=critical,CA:TRUE' -addext 'keyUsage=critical,keyCertSign,cRLSign'
  openssl req -new -newkey rsa:2048 -nodes -keyout "$state/browser.key" -out "$state/browser.csr" -subj '/CN=127.0.0.1'
  cat > "$state/browser.ext" <<'EOF'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=IP:127.0.0.1
EOF
  openssl x509 -req -in "$state/browser.csr" -CA "$state/ca.crt" -CAkey "$state/ca.key" -CAcreateserial \
    -out "$state/browser.crt" -days 1 -extfile "$state/browser.ext"
  chmod 600 "$state/ca.key" "$state/browser.key"
  openssl verify -CAfile "$state/ca.crt" -purpose sslserver -verify_ip 127.0.0.1 "$state/browser.crt"
  sudo cp "$state/ca.crt" /usr/local/share/ca-certificates/chanter-browser-fixture.crt
  sudo update-ca-certificates
  sudo apt-get install --yes libnss3-tools
  mkdir -p "$HOME/.pki/nssdb"
  if [[ ! -f "$HOME/.pki/nssdb/cert9.db" ]]; then
    certutil -N --empty-password -d "sql:$HOME/.pki/nssdb"
  fi
  certutil -A -d "sql:$HOME/.pki/nssdb" -n 'Chanter hosted browser fixture' -t 'C,,' -i "$state/ca.crt"
  node --input-type=module - "$state/ca.crt" "$state/firefox-policies.json" <<'JS'
import { writeFileSync } from 'node:fs';
writeFileSync(process.argv[3], JSON.stringify({ policies: { Certificates: { Install: [process.argv[2]] } } }));
JS
  # Playwright's patched Firefox loads its explicit policy path, not distribution/.
  printf 'PLAYWRIGHT_FIREFOX_POLICIES_JSON=%s\n' "$state/firefox-policies.json" >> "$GITHUB_ENV"
  printf 'NODE_EXTRA_CA_CERTS=%s\n' "$state/ca.crt" >> "$GITHUB_ENV"
  cat > "$state/Caddyfile" <<EOF
{
  admin off
  auto_https off
  persist_config off
  storage file_system {
    root "$state/caddy-storage"
  }
}
http://127.0.0.1:9419 {
  handle /livekit/* {
    route {
      request_header X-Chanter-LiveKit-Token {query.access_token}
      forward_auth 127.0.0.1:8082 {
        uri /internal/v1/media/join-authorization
      }
      request_header -X-Chanter-LiveKit-Token
      uri strip_prefix /livekit
      reverse_proxy 127.0.0.1:7880
    }
  }
  handle /api/* {
    reverse_proxy 127.0.0.1:8080
  }
  handle_path /moderation-proof/* {
    root * "$state/client"
    file_server
  }
  handle {
    root * "$ROOT/frontend/dist"
    try_files {path} /index.html
    file_server
  }
}
https://127.0.0.1:9420 {
  tls "$state/browser.crt" "$state/browser.key"
  handle /api/* {
    reverse_proxy 127.0.0.1:8080
  }
  handle {
    root * "$ROOT/frontend/dist"
    try_files {path} /index.html
    file_server
  }
}
EOF
  "$state/caddy" adapt --config "$state/Caddyfile" --adapter caddyfile --validate > "$state/adapted.json"
  product_spawn_detached "$ROOT/.product/pids/moderation-caddy.pid" "$ROOT/.product/logs/moderation-caddy.log" \
    "$state/caddy" run --config "$state/Caddyfile" --adapter caddyfile
  product_wait_for_url http://127.0.0.1:9419 'moderation production frontend' 20 1
  curl --fail --silent --show-error --cacert "$state/ca.crt" https://127.0.0.1:9420/ > /dev/null
  ;;
run)
  product_load_env
  export PLAYWRIGHT_MODERATION=1 PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1:9419
  node scripts/product/seed-moderation-proof.mjs
  (cd frontend && npx playwright test e2e/moderation-product.spec.ts --project chromium-1280)
  ;;
stop)
  product_stop_pid_file "$ROOT/.product/pids/moderation-caddy.pid" moderation-caddy
  ;;
*) echo 'Usage: moderation-browser.sh prepare|run|stop' >&2; exit 1 ;;
esac
