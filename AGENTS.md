# AGENTS.md

## Cursor Cloud specific instructions

This section captures durable, non-obvious context for running Chanter inside a Cursor Cloud
agent VM. Standard commands live in the root `Makefile` and `docs/operations/getting-started.md`;
prefer those and only rely on the caveats below.

### Product overview
Chanter is a Discord-like learning platform. It ships as two deployables:
- **Backend** (`backend/`): Java 21 + Spring Boot multi-module Maven reactor (~10 services:
  gateway 8080, auth 8081, community 8082, message 8083, media 8084, agent 8085, analytics 8086,
  realtime 8087, search 8088).
- **Frontend** (`frontend/`): React + Vite + TypeScript dev server on port 5173.
- **Infra** (`infra/docker-compose.yml`): Postgres, Redis, LiveKit (required), plus Redpanda and
  MinIO (started but not yet referenced by any code — effectively optional today).

### Toolchain (already provisioned in the VM snapshot)
- Java 21, Node 22, Maven, Docker + compose plugin, `python3`, `lsof` are installed.
- Maven 3.8.x is sufficient even though docs mention 3.9+ (no version enforcer in the POMs).

### Docker daemon must be started manually each session
The Docker daemon does **not** auto-start in the VM. Before `make product-up` / `make infra-up`,
start it and make the socket usable:

```bash
sudo dockerd >/tmp/dockerd.log 2>&1 &   # or run in a tmux session
sleep 5
sudo chmod 666 /var/run/docker.sock
```

The daemon is configured for docker-in-docker via `/etc/docker/daemon.json`
(`fuse-overlayfs` storage driver + `containerd-snapshotter` disabled, required for Docker 29).
Do not remove that config.

### Running the full stack
- One command: `make product-up` (starts infra via Docker, builds backend, launches all Java
  services from built JARs, then the frontend). First run pulls Docker images.
- Health: `make product-health` (checks gateway 8080, auth, realtime 8087, LiveKit 7880).
- Seed demo data: `make product-demo-seed` (needs `DEMO_PASSWORD` in `.env`; the repo default is
  `chanter-dev-demo`). Demo logins: `dev-demo-owner@chanter.local` /
  `dev-demo-learner@chanter.local`.
- Stop: `make product-down`. Logs: `.product/logs/<service>.log`.

### `.env` requirements
`make product-up` auto-copies `.env.example` → `.env` if missing. `CHANTER_JWT_SECRET` and
`CHANTER_INTERNAL_SERVICE_TOKEN` must be ≥32 chars (the example values satisfy this). Add
`DEMO_PASSWORD=chanter-dev-demo` for the seed script. `.env` is gitignored.

### Lint / test / build
- Frontend (in `frontend/`): `npm run lint`, `npm test` (Vitest), `npm run build`.
- Backend (in `backend/`): `mvn -B verify` (or `make backend-test`); no separate lint step exists.
- Backend service modules depend on the `common` module artifact in the local `.m2`, so run
  `mvn -B -q install -DskipTests` in `backend/` before running a single service via
  `spring-boot:run` (`make product-up` and the `make backend-*` targets already do this).
