# AGENTS.md

Project-specific guidance for AI agents. For product/architecture context start with `README.md`, `HANDOFF.md`, and `docs/operations/getting-started.md`. For the mandatory branch → PR → gated-merge workflow see `docs/operations/agent-workflow.md` and `.cursor/rules/git-workflow.mdc`.

## Cursor Cloud specific instructions

The update script installs/refreshes dependencies only. Everything below is about starting and running the stack; standard commands live in the root `Makefile` and `README.md` (§ Local Development) — prefer those instead of duplicating them here.

### Services overview
Chanter is a Java 21 / Spring Boot 3 microservices backend (`backend/`, Maven multi-module) behind a Spring Cloud Gateway (`:8080`), a React 19 + Vite frontend (`frontend/`, `:5173`, proxies `/api` + `/actuator` to the gateway), and Docker Compose infra (`infra/docker-compose.yml`: Postgres, Redis, Redpanda, MinIO, LiveKit). `make product-up` builds the backend and launches all services + frontend as detached processes (PIDs/logs under `.product/`); `make product-down` stops them; `make product-health` verifies the stack.

### Startup caveats (non-obvious)
- **Docker daemon must be started manually** before any infra/`make product-up`/`make infra-up`: run `sudo dockerd` (e.g. in a background tmux session). It is not auto-started on boot.
- **Redpanda requires the `epoll` Seastar reactor backend in this VM.** The default `linux-aio` backend crashes on startup (`close() syscall failed: Invalid argument`). The fix (`--reactor-backend=epoll`) is committed in `infra/docker-compose.yml`; if Redpanda ever fails to become healthy, confirm that flag is still present.
- **`.env` is required.** The product scripts auto-copy `.env.example` → `.env` if missing. `make product-demo-seed` additionally needs `DEMO_PASSWORD` set in `.env` (e.g. `DEMO_PASSWORD=chanter-dev-demo`); it is not in `.env.example`.

### Testing caveats (non-obvious)
- **Do NOT `source .env` before running backend tests.** `mvn test` / `make backend-test` rely on the `test` Spring profile, but exported `CHANTER_INTERNAL_SERVICE_TOKEN` / `CHANTER_JWT_SECRET` env vars override the test-profile config and cause spurious failures (e.g. `NotificationSmokeTest` returns 401). Run backend tests in a clean shell (unset those vars). Backend tests use in-memory H2, so infra does not need to be running for them.
- Frontend tests/lint (`npm test` = Vitest, `npm run lint` = ESLint) run without infra.

### Runtime notes
- The auth login API is `POST /api/v1/auth/login` (`/sign-in` is a frontend route, not an API path). Demo logins after seeding: `dev-demo-owner@chanter.local` / `dev-demo-learner@chanter.local`, password from `DEMO_PASSWORD`.
- The AI Study Assistant answers from AI-approved course resources via local RAG/keyword matching; an LLM is optional and disabled by default (`CHANTER_LLM_ENABLED=false`). Low-confidence ("not enough approved material") answers are normal expected behavior, not a failure.
- Service logs are under `.product/logs/<service>.log` when running via `make product-up`.
