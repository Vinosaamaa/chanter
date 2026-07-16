# AGENTS.md

## Cursor Cloud specific instructions

Chanter is a Spring Boot microservice backend (Java 21, Maven, multi-module under `backend/`) plus a
React/TypeScript/Vite frontend (`frontend/`). Local infra (PostgreSQL, Redis, Redpanda, MinIO, LiveKit)
runs in Docker. Standard commands live in the root `Makefile` and `docs/operations/getting-started.md`;
prefer those instead of re-deriving them.

### Starting services (non-obvious caveats)

- **Docker daemon is not auto-started** in this VM (no systemd). Before any `make product-up`,
  `make infra-up`, or backend test that needs Testcontainers, ensure the daemon is running:
  `sudo dockerd > /tmp/dockerd.log 2>&1 &` then `sudo chmod 666 /var/run/docker.sock`.
  The daemon is configured for `fuse-overlayfs` with the containerd snapshotter disabled
  (`/etc/docker/daemon.json`) — required for Docker 29 in this sandbox.
- **Full stack:** `make product-up` builds all backend jars, starts the 4 infra containers + LiveKit,
  all 10 Java services (on the host, not in Docker), realtime-service, and the Vite frontend
  (http://localhost:5173, proxying `/api` + `/actuator` to the gateway at :8080). Verify with
  `make product-health`; stop with `make product-down`. Logs: `.product/logs/`.
- **Demo data:** `make product-demo-seed` creates two users (`dev-demo-owner@chanter.local` /
  `dev-demo-learner@chanter.local`, password from `DEMO_PASSWORD` in `.env`, default `chanter-dev-demo`),
  a Study Server, course channels, a friendship, and the AI Study Assistant. Re-runnable (idempotent).
- `.env` is required and gitignored; `make`/scripts auto-create it from `.env.example`, which already
  contains valid 32+ char local `CHANTER_JWT_SECRET` and `CHANTER_INTERNAL_SERVICE_TOKEN`. Add
  `DEMO_PASSWORD=chanter-dev-demo` for seeding.

### Testing

- Frontend: `cd frontend && npm run lint`, `npm test` (vitest), `npm run build`. Playwright e2e
  (`npm run test:e2e:critical`) needs the stack running and `npx playwright install chromium`.
- Backend: `cd backend && mvn -B test`. Known flaky test:
  `realtime-service` `SocialRealtimeWebSocketSmokeTest.friendPresenceAndDirectMessagesFanOutOverWebSocket`
  can hit a 30s WebSocket blocking-read timeout under CPU contention during the full parallel suite; it
  passes reliably when re-run in isolation
  (`mvn -pl realtime-service test -Dtest=SocialRealtimeWebSocketSmokeTest`). This is a timing/resource
  issue, not a code defect — re-run rather than "fixing" it.

### Tooling

- Java 21 is the default `java`. Maven and Node 22 are on PATH. `mvn` is 3.8.x (docs say 3.9+, but the
  build works on 3.8). No Maven/Gradle wrapper — invoke `mvn` directly.
