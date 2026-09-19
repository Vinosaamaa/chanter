#!/bin/sh
set -eu
# Only runs when PostgreSQL initializes an empty volume. No passwords enter SQL logs.
for service in auth community message media agent search notification; do
  key="DB_$(printf '%s' "$service" | tr '[:lower:]' '[:upper:]')_PASSWORD"
  password="$(printenv "$key")"
  [ -n "$password" ] || { echo "Missing database credential: $key" >&2; exit 1; }
  psql --username "$POSTGRES_USER" --dbname postgres --set=ON_ERROR_STOP=1 \
    --set=role="chanter_$service" --set=password_env="$key" <<'SQL'
\getenv password :password_env
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'role', :'role') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'role') \gexec
SQL
done
