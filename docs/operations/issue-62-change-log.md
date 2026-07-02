# Issue #62 — change log

## Scope

One-command local product stack for Workable Product phase (#60 epic).

## Changes

| Area | Change |
|------|--------|
| `Makefile` | `product-up`, `product-down`, `product-health`, `product-test` targets |
| `scripts/product/` | Orchestration: `up.sh`, `down.sh`, `health.sh`, `lib.sh`, `lib.test.sh` |
| `infra/docker-compose.yml` | `product` profile for `realtime-service` + LiveKit dev server |
| `.env.example` | `REALTIME_*`, `LIVEKIT_*`, `FRONTEND_PORT` defaults |
| `.gitignore` | `.product/` runtime logs and PIDs |
| `README.md`, `HANDOFF.md`, `infra/README.md` | Product stack docs |

## Usage

```bash
cp .env.example .env
make product-up       # infra + realtime + LiveKit + all Java services + frontend
make product-health   # gateway, auth, realtime, LiveKit checks
make product-down     # stop app processes + product Docker services
```

## Ports (happy path)

| Surface | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Gateway | http://localhost:8080 |
| Realtime | http://localhost:8087 |
| LiveKit | ws://localhost:7880 |

## Tests

```bash
make product-test
docker compose -f infra/docker-compose.yml --profile product config
```

## Deferred

- LiveKit token/signaling integration — **#61**
- E2E demo checklist — **#63**
