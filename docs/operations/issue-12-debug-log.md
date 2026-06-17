# Issue 12 Debug Log: Study Server Create Flow

Date: 2026-06-17  
Branch: `feature/12-create-study-server`  
Issue: `#12 Slice: Create A Study Server`

## Summary

While verifying the Study Server create flow in the in-app browser at `http://127.0.0.1:5173/`, two local runtime errors appeared after the implementation was already passing automated tests:

1. `Create failed with 502`
2. `Create failed with 403`

Both failures were local development wiring issues, not Community Service domain failures.

## Error 1: 502 From Create Study Server

Observed behavior:

- The frontend submitted `POST /api/v1/study-servers`.
- Vite proxied the request toward Gateway.
- Gateway could not reach the backend route and the browser showed `Create failed with 502`.

What I checked:

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8082/actuator/health
curl -i http://127.0.0.1:8081/api/v1/auth/health
lsof -nP -iTCP:8080 -iTCP:8081 -iTCP:8082 -sTCP:LISTEN
```

Finding:

- No Spring Boot services were listening on `8080`, `8081`, or `8082`.
- Docker infrastructure was already healthy: Postgres, Redis, Redpanda, and MinIO were up.

First attempted fix:

```bash
make backend-community
make backend-gateway
make backend-auth
```

That failed because the shell default Java was 17, while the project compiles for Java 21:

```text
UnsupportedClassVersionError: ... class file version 65.0, this version ... recognizes ... up to 61.0
```

Working fix:

```bash
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home make backend-community
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home make backend-gateway
JAVA_HOME=/Users/wenkxu/Library/Java/JavaVirtualMachines/corretto-21.0.5/Contents/Home make backend-auth
```

Verification:

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"name":"Browser Retry Study Server","ownerUserId":"00000000-0000-4000-8000-000000000123"}' \
  http://127.0.0.1:8080/api/v1/study-servers
```

Result:

- Gateway returned `201 Created`.
- Response included the Owner role and default channels: `announcements`, `general`, `study-room`.

## Error 2: 403 From Browser Create Request

Observed behavior:

- Backend services were running and health checks showed healthy in the UI.
- Command-line requests through Gateway and Vite returned `201 Created`.
- The in-app browser still showed `Create failed with 403`.

What I checked:

- Browser DOM showed all services healthy and the stale form error:

```text
Gateway UP
Auth ok
Community ok
Create failed with 403
```

- Replayed the exact browser payload through Vite and Gateway with `curl`; both succeeded.
- Compared browser behavior against command-line behavior.

Finding:

- Browser `fetch` sends an `Origin: http://127.0.0.1:5173` header.
- Gateway CORS allowed only `http://localhost:5173`.
- Command-line `curl` did not send the browser `Origin` header unless explicitly provided, so the first probes missed the CORS mismatch.

Fix in `backend/gateway-service/src/main/resources/application.yml`:

```yaml
allowedOrigins:
  - "http://localhost:5173"
  - "http://127.0.0.1:5173"
```

Verification with browser-origin header:

```bash
curl -i \
  -H 'Origin: http://127.0.0.1:5173' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Origin Header Study Server","ownerUserId":"00000000-0000-4000-8000-000000000789"}' \
  http://127.0.0.1:8080/api/v1/study-servers
```

Result:

```text
HTTP/1.1 201 Created
Access-Control-Allow-Origin: http://127.0.0.1:5173
```

Final browser verification:

- Reloaded `http://127.0.0.1:5173/`.
- Clicked `Create Study Server`.
- UI landed in the Study Server shell.
- Visible result included:
  - `STUDY SERVER OWNER`
  - `announcements`
  - `general`
  - `study-room`

## Lessons For Future Agents

- When a browser request fails but `curl` succeeds, repeat the request with the browser `Origin` header.
- For local Vite testing, allow both `http://localhost:5173` and `http://127.0.0.1:5173` in Gateway CORS.
- Verify all local backend ports before debugging frontend behavior:
  - Gateway: `8080`
  - Auth: `8081`
  - Community: `8082`
- Always run local Spring services with Java 21 for this repository.
- If Vite shows `http proxy error: ECONNREFUSED`, the frontend is alive but the target backend is not.
- If an unmerged Flyway migration is edited after it has already run against local Docker Postgres, the local database can keep the old migration checksum. Before re-running Community Service in that case, reset the local Postgres volume or use Flyway repair only when you intentionally want to preserve local data.

## Local Flyway Checksum Note

During Greptile review, the issue #12 migration changed before merge:

```sql
created_at TIMESTAMP WITH TIME ZONE NOT NULL
```

If your local Docker Postgres already ran the older `V1__create_study_server_tables.sql`, restarting Community Service may fail Flyway validation because the checksum in `flyway_schema_history` no longer matches the file on disk.

For local development, the cleanest fix is to reset local Docker data and start infra again:

```bash
docker compose -f infra/docker-compose.yml down -v
make infra-up
```

Only use `flyway repair` if you understand that it updates migration metadata for the existing local database. For this early branch, dropping the local volume is simpler and safer.
