# Issue #62 — CodeRabbit fix log (PR #77)

## Pass 1

| Comment | Action |
|---------|--------|
| LiveKit image stale; `LIVEKIT_KEYS` not wired | Fixed — `v1.13.1` + `LIVEKIT_KEYS` from `.env` |
| `pg_isready` races container startup | Fixed — `docker compose up --wait` |
| `spring-boot:run` orphan JVM on down | Fixed — `pkill -P` process tree in `product_stop_pid_file` |

## Deferred

None.
