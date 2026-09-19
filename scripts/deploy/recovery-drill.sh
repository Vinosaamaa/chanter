#!/usr/bin/env bash
set -euo pipefail
image="${1:?Usage: recovery-drill.sh reviewed-postgres-image}"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
mkdir -p "$root/.cache/recovery-drill"
state="$(mktemp -d "$root/.cache/recovery-drill/run.XXXXXX")"
project="chanter-recovery-$(basename "$state" | tr '[:upper:].' '[:lower:]-')"
source="$project-source"
restored="$project-restored"
repo="$project-repository"
source_volume="$project-original-data"
restore_volume="$project-restored-data"
wrong_volume="$project-wrong-key-data"
cleanup() {
  docker rm -f "$source" "$restored" >/dev/null 2>&1 || true
  docker volume rm "$source_volume" "$restore_volume" "$wrong_volume" "$repo" >/dev/null 2>&1 || true
}
trap cleanup EXIT
umask 077
{
  printf 'POSTGRES_USER=chanter_admin\nPOSTGRES_PASSWORD=%s\n' "$(openssl rand -hex 32)"
  printf 'PGBACKREST_STANZA=chanter\nPGBACKREST_PG1_USER=chanter_admin\n'
  printf 'PGBACKREST_PG1_PATH=/var/lib/postgresql/data\nPGBACKREST_PG1_SOCKET_PATH=/var/run/postgresql\n'
  printf 'PGBACKREST_REPO1_TYPE=posix\nPGBACKREST_REPO1_PATH=/var/lib/pgbackrest/repo\n'
  printf 'PGBACKREST_REPO1_CIPHER_TYPE=aes-256-cbc\nPGBACKREST_REPO1_CIPHER_PASS=%s\n' "$(openssl rand -hex 32)"
  printf 'PGBACKREST_REPO1_RETENTION_FULL=2\nPGBACKREST_PROCESS_MAX=1\nPGBACKREST_START_FAST=y\n'
  printf 'PGBACKREST_LOG_LEVEL_CONSOLE=warn\nPGBACKREST_LOG_LEVEL_FILE=off\nPGBACKREST_LOCK_PATH=/tmp/pgbackrest\n'
} > "$state/backup.env"
for volume in "$repo" "$source_volume" "$restore_volume" "$wrong_volume"; do
  docker volume create --label "chanter.recovery-drill=$project" "$volume" >/dev/null
done
docker run -d --name "$source" --network none --env-file "$state/backup.env" \
  --volume "$source_volume:/var/lib/postgresql/data" --volume "$repo:/var/lib/pgbackrest/repo" \
  "$image" postgres -c archive_mode=on -c 'archive_command=pgbackrest archive-push %p' -c archive_timeout=60 >/dev/null
ready() {
  for attempt in $(seq 1 60); do
    if docker exec "$1" pg_isready -h 127.0.0.1 -U chanter_admin >/dev/null 2>&1; then return; fi
    sleep 1
  done
  echo 'Recovery drill database did not become ready.' >&2
  return 1
}
ready "$source"
psql_source() { docker exec "$source" psql -v ON_ERROR_STOP=1 -U chanter_admin -d postgres -Atc "$1"; }
psql_source 'CREATE TABLE recovery_marker (id integer PRIMARY KEY); INSERT INTO recovery_marker VALUES (1);' >/dev/null
docker exec "$source" pgbackrest stanza-create
docker exec "$source" pgbackrest check
docker exec "$source" pgbackrest --type=full backup
psql_source 'INSERT INTO recovery_marker VALUES (2)' >/dev/null
# Separate commands commit marker 2 before the recovery point is written.
psql_source "SELECT pg_create_restore_point('chanter_drill_target')" >/dev/null
psql_source 'INSERT INTO recovery_marker VALUES (3)' >/dev/null
psql_source 'SELECT pg_switch_wal()' >/dev/null
docker exec "$source" pgbackrest check
sed 's/^PGBACKREST_REPO1_CIPHER_PASS=.*/PGBACKREST_REPO1_CIPHER_PASS=deliberately-wrong-fixture-key/' \
  "$state/backup.env" > "$state/wrong-key.env"
if docker run --rm --network none --env-file "$state/wrong-key.env" \
    --volume "$wrong_volume:/var/lib/postgresql/data" --volume "$repo:/var/lib/pgbackrest/repo:ro" \
    "$image" pgbackrest --type=name --target=chanter_drill_target --archive-mode=off restore \
    > "$state/wrong-key.log" 2>&1; then
  echo 'Encrypted restore unexpectedly accepted the wrong key.' >&2; exit 1
fi
started="$(date +%s)"
docker run --rm --network none --env-file "$state/backup.env" \
  --volume "$restore_volume:/var/lib/postgresql/data" --volume "$repo:/var/lib/pgbackrest/repo:ro" \
  "$image" pgbackrest --type=name --target=chanter_drill_target --target-action=promote --archive-mode=off restore
docker run -d --name "$restored" --network none --env-file "$state/backup.env" \
  --volume "$restore_volume:/var/lib/postgresql/data" --volume "$repo:/var/lib/pgbackrest/repo:ro" \
  "$image" postgres -c archive_mode=off >/dev/null
ready "$restored"
for attempt in $(seq 1 60); do
  test "$(docker exec "$restored" psql -U chanter_admin -d postgres -Atc 'SELECT pg_is_in_recovery()')" = f && break
  sleep 1
done
test "$(docker exec "$restored" psql -U chanter_admin -d postgres -Atc 'SELECT pg_is_in_recovery()')" = f
actual="$(docker exec "$restored" psql -v ON_ERROR_STOP=1 -U chanter_admin -d postgres -Atc 'SELECT string_agg(id::text, chr(44) ORDER BY id) FROM recovery_marker')"
test "$actual" = '1,2' || { echo 'Point-in-time restore included the wrong commits.' >&2; exit 1; }
test "$(psql_source 'SELECT count(*) FROM recovery_marker')" = 3
test "$(docker exec "$restored" psql -U chanter_admin -d postgres -Atc 'SHOW archive_mode')" = off
echo "Encrypted isolated PostgreSQL restore passed; fixture recovery took $(($(date +%s) - started)) seconds."
echo 'This local repository drill does not establish production off-host RPO or RTO.'
