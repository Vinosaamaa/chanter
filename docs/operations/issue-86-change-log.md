# Issue #86 — change log

## Scope

Product stack reliability hotfixes so `make product-up`, channel realtime, DMs, demo seed, and AI install work reliably on macOS local dev.

## Changes

| Area | Change |
|------|--------|
| `scripts/product/up.sh` | Run **realtime-service on host** (not Docker); start services with **`java -jar`** + `disown`; stop Docker realtime container to avoid port 8087 conflict |
| `scripts/product/lib.sh` | Port-based running/stop checks; `product_module_jar`; add realtime to `product_java_modules` |
| `scripts/product/down.sh` | Stop processes by listening port |
| `scripts/seed-workable-product-demo.sh` | Health gate via `product_load_env`; clearer curl errors; `courseResources` idempotency |
| `Makefile` | `product-demo-seed` runs health first; `product-cleanup-demo-servers`; default `DEMO_PASSWORD` |
| `scripts/cleanup-duplicate-demo-servers.sh` | Remove duplicate demo Study Servers (keeps newest Workable Product Demo) |
| `agent-service` HTTP clients | `X-User-Id` header for media/FAQ (fixes AI install-preview 502) |
| `infra/docker-compose.yml` | LiveKit `LIVEKIT_KEYS` space after colon |
| `realtime-service` | Clearer community access error on subscribe failure |
| `docs/issues/public-launch-issue-breakdown.md` | Public Launch phase breakdown (#82–#104) |
| `scripts/create-public-launch-issues.sh` | Idempotent issue publisher (already run on GitHub) |

## Verify

```bash
make product-down && make product-up && make product-health && make product-demo-seed
make product-cleanup-demo-servers   # optional
```

- #announcements: no "Realtime request failed" banner; messages send
- Friends Hub: DM between owner and learner with both on `/app/friends`
- Ask AI works after seed (agent-service media auth fix)

## GitHub projects

Linked user projects **#1, #3, #4, #5** to `Vinosaamaa/chanter` so they appear on the repository **Projects** tab.

## cubic

See `docs/operations/issue-86-cubic-fix.md` — pass 1 addressed 16 actionable findings.
