# Issue #63 — change log

## Scope

Capstone E2E demo path for Workable Product (#60 epic): document and bootstrap the full local click-through from cold start to voice (and optional DM call).

## Changes

| Area | Change |
|------|--------|
| `docs/operations/getting-started.md` | Beginner guide: install, start stack, try each feature |
| `docs/operations/workable-product-demo.md` | Canonical demo checklist: cold start → seed → two users → live text → DM → voice → optional DM call |
| `scripts/seed-workable-product-demo.sh` | Idempotent seed: demo personas, Study Server, enrollment, friendship, **AI assistant install** |
| `docs/operations/ai-study-assistant.md` | How Study Assistant works today (keyword grounding, not OpenAI) |
| `Makefile` | `product-demo-seed` target |
| `.env.example` | `DEMO_PASSWORD` default for demo scripts |
| `infra/docker/realtime-service/Dockerfile` | Copy full `backend/` tree so `make product-up` Docker build succeeds |
| `README.md`, `HANDOFF.md` | Link demo doc as definition of workable local product |

## Usage

```bash
cp .env.example .env
make product-up
make product-health
make product-demo-seed
# Follow docs/operations/workable-product-demo.md §3
```

## Tests

```bash
make product-test
docker build -f infra/docker/realtime-service/Dockerfile .
```

## Deferred

- Production UI for sending/accepting friend requests (demo uses seed script + API today)
- Automated browser E2E (Playwright) — manual checklist for #63
