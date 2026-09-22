#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
architecture="${1:?Usage: smoke-release.sh arm64|amd64}"
case "$architecture" in arm64|amd64) ;; *) echo 'Unsupported architecture' >&2; exit 2 ;; esac
bundle="$root/.cache/release/$architecture"
mkdir -p "$root/.cache/release-smoke/$architecture"
state="$(mktemp -d "$root/.cache/release-smoke/$architecture/staging.XXXXXX")"
project="chanter-smoke-$architecture-$(basename "$state" | tr '[:upper:].' '[:lower:]-')"
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
const backupFile = process.argv[2] + '/runtime/backup.env';
let backup = fs.readFileSync(backupFile, 'utf8');
for (const [key, value] of Object.entries({ CHANTER_BACKUP_S3_ENDPOINT: 'https://backup.chanter.test',
  CHANTER_BACKUP_S3_BUCKET: 'fixture-backup', CHANTER_BACKUP_S3_REGION: 'fixture-region',
  CHANTER_BACKUP_S3_ACCESS_KEY: 'fixture-backup-access', CHANTER_BACKUP_S3_SECRET_KEY: 'fixture-backup-secret' })) {
  backup = backup.replace(new RegExp('^' + key + '=$', 'm'), key + '=' + value);
}
fs.writeFileSync(backupFile, backup, { mode: 0o600 });
JS
compose_file="$(node scripts/deploy/host.mjs render "$bundle" "$state")"
node --input-type=module - "$compose_file" <<'JS'
import fs from 'node:fs';
import path from 'node:path';
const file = process.argv[2]; const compose = JSON.parse(fs.readFileSync(file));
for (const [name, service] of Object.entries(compose.services)) {
  if (name.endsWith('-service') && !name.startsWith('migrate-')) Object.assign(service.environment, {
    CHANTER_TELEMETRY_ENABLED: 'true', OTEL_TRACES_EXPORTER: 'none', OTEL_METRICS_EXPORTER: 'none',
    OTEL_JAVAAGENT_EXTENSIONS: '/app/telemetry/privacy.jar', OTEL_JAVAAGENT_LOGGING: 'application',
  });
}
compose.services.frontend.environment.CHANTER_TLS_DIRECTIVE = 'tls internal';
// This isolated startup test has no external provider credentials. Production remains forced to S3.
compose.services['media-service'].environment.CHANTER_MEDIA_STORAGE_BACKEND = 'local';
compose.services['media-service'].volumes = compose.services['media-service'].volumes
  .map(volume => volume === 'resources:/app/resources:ro' ? 'resources:/app/resources' : volume);
// The real encrypted local backup exercises the same archive/backup commands;
// separate provider drills must prove the configured off-host S3 repository.
const backupFile = path.join(path.dirname(file), 'postgres-backup.env');
const backup = fs.readFileSync(backupFile, 'utf8').split('\n')
  .filter(line => !/^PGBACKREST_REPO1_(S3_|STORAGE_)/.test(line))
  .map(line => line.startsWith('PGBACKREST_REPO1_TYPE=') ? 'PGBACKREST_REPO1_TYPE=posix'
    : line.startsWith('PGBACKREST_REPO1_PATH=') ? 'PGBACKREST_REPO1_PATH=/var/lib/pgbackrest/repo' : line).join('\n');
fs.writeFileSync(backupFile, backup, { mode: 0o600 });
compose.services.postgres.volumes.push('backup-repository:/var/lib/pgbackrest/repo');
compose.volumes['backup-repository'] = {};
fs.writeFileSync(file, JSON.stringify(compose, null, 2));
JS
compose=(docker compose --project-name "$project" -f "$compose_file")
cleanup() { "${compose[@]}" down --volumes --remove-orphans; }
trap cleanup EXIT
"${compose[@]}" config --quiet
# Parse the actual packaged proxy configuration before booting the application.
# Adapt does not start a listener or provision certificates.
"${compose[@]}" run --rm --no-deps --entrypoint caddy frontend adapt --config /etc/caddy/Caddyfile --adapter caddyfile >/dev/null
"${compose[@]}" up -d --wait --wait-timeout 180 postgres redis
export RESTIC_REPOSITORY="$state/configuration-repository"
export RESTIC_PASSWORD="$(openssl rand -hex 32)"
"$bundle/tools/restic" --no-cache init >/dev/null
node --input-type=module - "$state" "$bundle" <<'JS' | "$bundle/tools/restic" --no-cache backup --stdin --stdin-filename configuration.json --host chanter-staging --json > "$state/configuration-backup.jsonl"
import fs from 'node:fs';
import { configurationSnapshot } from './scripts/deploy/host.mjs';
process.stdout.write(JSON.stringify(configurationSnapshot(process.argv[2], JSON.parse(fs.readFileSync(process.argv[3] + '/release.json')))));
JS
config_snapshot="$(node -e 'const rows=require("fs").readFileSync(process.argv[1],"utf8").trim().split("\n").map(JSON.parse); process.stdout.write(rows.find(r=>r.message_type==="summary").snapshot_id)' "$state/configuration-backup.jsonl")"
"${compose[@]}" exec -T postgres pgbackrest stanza-create
"${compose[@]}" exec -T postgres pgbackrest check
backup_release="$(node -p 'JSON.parse(require("fs").readFileSync(process.argv[1])).commit' "$bundle/release.json")"
"${compose[@]}" exec -T postgres pgbackrest --type=incr "--annotation=release=$backup_release" "--annotation=config-snapshot=$config_snapshot" backup
"${compose[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U chanter_admin -d chanter_agent \
  -c 'CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public'
test "$("${compose[@]}" exec -T postgres psql -U chanter_admin -d chanter_agent -Atc "SELECT rolsuper FROM pg_roles WHERE rolname='chanter_agent'")" = f
"${compose[@]}" up -d --no-deps --wait --wait-timeout 600 clamav
python3 scripts/deploy/check-scanner.py "$project" "$compose_file"
mapfile -t databases < <(node --input-type=module -e 'import {databaseModules} from "./scripts/deploy/release.mjs"; console.log(databaseModules.join("\n"))')
for module in "${databases[@]}"; do "${compose[@]}" --profile migration run --rm --no-deps "migrate-$module"; done
# A second run validates Flyway's durable once-only history, rather than relying on a local marker.
for module in "${databases[@]}"; do "${compose[@]}" --profile migration run --rm --no-deps "migrate-$module"; done
indexes="$("${compose[@]}" exec -T postgres psql -U chanter_admin -d chanter_message -Atc \
  "SELECT count(*) FROM pg_indexes WHERE indexname IN ('uq_friend_requests_pending_pair', 'uq_ta_queue_open_support_question')")"
test "$indexes" = 2 || { echo 'PostgreSQL-specific migrations did not complete.' >&2; exit 1; }
native_history="$("${compose[@]}" exec -T postgres psql -U chanter_admin -d chanter_agent -Atc \
  "SELECT string_agg(version, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version IN ('9','10','11','12','13') AND success")"
test "$native_history" = '9,10,11,12,13' || { echo 'Extraction, native and semantic vector migrations did not complete in order.' >&2; exit 1; }
native_message="$("${compose[@]}" exec -T postgres psql -U chanter_admin -d chanter_message -Atc \
  "SELECT count(*) FROM flyway_schema_history WHERE version='10' AND success")"
test "$native_message" = 1 || { echo 'Accepted-answer consumer migration did not complete exactly once.' >&2; exit 1; }
mapfile -t modules < <(node --input-type=module -e 'import {modules} from "./scripts/deploy/release.mjs"; console.log(modules.join("\n"))')
for module in "${modules[@]}"; do
  started="$(date +%s)"
  if ! "${compose[@]}" up -d --no-deps --wait --wait-timeout 180 "$module"; then
    # Report only bounded code locations from structured exceptions. Never print
    # free-form startup messages, runtime settings or container environments.
    "${compose[@]}" logs --no-log-prefix --tail 80 "$module" | node --input-type=module -e '
      import readline from "node:readline";
      for await (const line of readline.createInterface({ input: process.stdin })) {
        let item; try { item = JSON.parse(line); } catch { continue; }
        if (item.event !== "application.exception" || !Array.isArray(item.errors)) continue;
        const token = value => typeof value === "string" && /^[A-Za-z_$][A-Za-z0-9_.$]{0,179}$/.test(value) ? value : "unknown";
        console.log(JSON.stringify({ errors: item.errors.slice(0, 4).map(error => ({ type: token(error.type),
          frames: (Array.isArray(error.frames) ? error.frames : []).slice(0, 12).map(frame => ({
            class: token(frame.class), method: token(frame.method), line: Number.isSafeInteger(frame.line) ? frame.line : 0 })) })) }));
      }'
    mapfile -t failed_containers < <("${compose[@]}" ps --all --quiet "$module")
    if [ "${#failed_containers[@]}" -gt 0 ]; then
      docker inspect --format 'exit={{.State.ExitCode}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}' "${failed_containers[@]}"
    fi
    exit 1
  fi
  echo "$module became ready in $(($(date +%s) - started)) seconds"
done
"${compose[@]}" up -d --no-deps livekit
"${compose[@]}" up -d --no-deps --wait --wait-timeout 180 frontend
test "$("${compose[@]}" exec -T postgres stat -c '%u:%g' /var/lib/postgresql/data)" = '70:70'
"${compose[@]}" exec -T postgres sh -c 'test "$(id -u)" = 70 && test ! -e /usr/local/bin/gosu'
test "$("${compose[@]}" exec -T redis stat -c '%u:%g' /data)" = '999:1000'
test "$("${compose[@]}" exec -T media-service stat -c '%u:%g' /app/resources)" = '10001:10001'
test "$("${compose[@]}" exec -T media-service stat -c '%u:%g' /app/media-spool)" = '10001:10001'
"${compose[@]}" exec -T media-service sh -c 'test -w /app/media-spool && test -S /run/clamav/clamd.sock'
test "$("${compose[@]}" exec -T frontend stat -c '%u:%g' /data/caddy)" = '10001:10001'
"${compose[@]}" exec -T media-service sh -c 'test -w /app/resources && test "$(id -u)" = 10001'
"${compose[@]}" exec -T frontend sh -c 'test -w /data/caddy && test -w /config/caddy && test "$(id -u)" = 10001'
"${compose[@]}" exec -T frontend caddy version | grep -q '^v2.11.4'
livekit_ready=false
for attempt in $(seq 1 30); do
  if "${compose[@]}" exec -T gateway-service java -cp /app/helpers Probe http://livekit:7880; then
    livekit_ready=true; break
  fi
  sleep 2
done
test "$livekit_ready" = true || { echo 'LiveKit signaling did not become ready.' >&2; exit 1; }
"${compose[@]}" cp frontend:/data/caddy/pki/authorities/local/root.crt "$state/root.crt"
printf '127.0.0.1 staging.chanter.test\n' | sudo tee -a /etc/hosts >/dev/null
NODE_EXTRA_CA_CERTS="$state/root.crt" node scripts/deploy/host.mjs verify staging.chanter.test "$state"
# Internal high ports must not leak into public HTTP-to-HTTPS redirects.
test "$(curl --silent --output /dev/null --write-out '%{redirect_url}' http://staging.chanter.test/sign-in)" = 'https://staging.chanter.test/sign-in'
echo "Runner processors: $(getconf _NPROCESSORS_ONLN)"
grep '^MemTotal:' /proc/meminfo
mapfile -t containers < <("${compose[@]}" ps --quiet)
docker stats --no-stream --format '{{.Name}} CPU={{.CPUPerc}} RAM={{.MemUsage}} PIDs={{.PIDs}}' "${containers[@]}"
echo 'Container health, migrations, TLS routing, static frontend and secure auth bootstrap passed.'
