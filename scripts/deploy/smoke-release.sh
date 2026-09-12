#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
architecture="${1:?Usage: smoke-release.sh arm64|amd64}"
bundle="$root/.cache/release/$architecture"
state="$root/.cache/release-smoke/$architecture/staging"
node scripts/deploy/host.mjs init "$state" staging staging.chanter.test 192.0.2.1
# Isolated smoke credentials are never sent to an SMTP provider. The database starts empty.
node --input-type=module - "$state" <<'JS'
import fs from 'node:fs';
const file = process.argv[2] + '/runtime/auth-service.env';
let text = fs.readFileSync(file, 'utf8');
for (const [key, value] of Object.entries({ CHANTER_EMAIL_FROM: 'noreply@staging.chanter.test',
  CHANTER_SMTP_HOST: 'smtp.chanter.test', CHANTER_SMTP_USERNAME: 'smoke-unused', CHANTER_SMTP_PASSWORD: 'smoke-unused' })) {
  text = text.replace(new RegExp('^' + key + '=$', 'm'), key + '=' + value);
}
fs.writeFileSync(file, text, { mode: 0o600 });
JS
compose_file="$(node scripts/deploy/host.mjs render "$bundle" "$state")"
node --input-type=module - "$compose_file" <<'JS'
import fs from 'node:fs';
const file = process.argv[2]; const compose = JSON.parse(fs.readFileSync(file));
compose.services.frontend.environment.CHANTER_TLS_DIRECTIVE = 'tls internal';
fs.writeFileSync(file, JSON.stringify(compose, null, 2));
JS
compose=(docker compose --project-name chanter-staging -f "$compose_file")
cleanup() { "${compose[@]}" down --volumes --remove-orphans; }
trap cleanup EXIT
"${compose[@]}" config --quiet
"${compose[@]}" up -d --wait --wait-timeout 180 postgres redis
mapfile -t databases < <(node --input-type=module -e 'import {databaseModules} from "./scripts/deploy/release.mjs"; console.log(databaseModules.join("\n"))')
for module in "${databases[@]}"; do "${compose[@]}" --profile migration run --rm --no-deps "migrate-$module"; done
# A second run validates Flyway's durable once-only history, rather than relying on a local marker.
for module in "${databases[@]}"; do "${compose[@]}" --profile migration run --rm --no-deps "migrate-$module"; done
indexes="$("${compose[@]}" exec -T postgres psql -U chanter_admin -d chanter_message -Atc \
  "SELECT count(*) FROM pg_indexes WHERE indexname IN ('uq_friend_requests_pending_pair', 'uq_ta_queue_open_support_question')")"
test "$indexes" = 2 || { echo 'PostgreSQL-specific migrations did not complete.' >&2; exit 1; }
mapfile -t modules < <(node --input-type=module -e 'import {modules} from "./scripts/deploy/release.mjs"; console.log(modules.join("\n"))')
for module in "${modules[@]}"; do
  started="$(date +%s)"
  "${compose[@]}" up -d --no-deps --wait --wait-timeout 180 "$module"
  echo "$module became ready in $(($(date +%s) - started)) seconds"
done
"${compose[@]}" up -d --no-deps livekit
"${compose[@]}" up -d --no-deps --wait --wait-timeout 180 frontend
"${compose[@]}" exec -T gateway-service java -cp /app/helpers Probe http://livekit:7880
"${compose[@]}" cp frontend:/data/caddy/pki/authorities/local/root.crt "$state/root.crt"
printf '127.0.0.1 staging.chanter.test\n' | sudo tee -a /etc/hosts >/dev/null
NODE_EXTRA_CA_CERTS="$state/root.crt" node scripts/deploy/host.mjs verify staging.chanter.test
echo "Runner processors: $(getconf _NPROCESSORS_ONLN)"
grep '^MemTotal:' /proc/meminfo
mapfile -t containers < <("${compose[@]}" ps --quiet)
docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} RAM={{.MemUsage}} PIDs={{.PIDs}}' "${containers[@]}"
echo 'Container health, migrations, TLS routing, static frontend and secure auth bootstrap passed.'
