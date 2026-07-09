# Chanter Infrastructure

Local development dependencies via Docker Compose.

## Services

| Service | Port | Profile |
|---------|------|---------|
| PostgreSQL 16 | 5432 | default |
| Redis 7 | 6379 | default |
| Redpanda | 19092 | default |
| MinIO | 9000 (API), 9001 (console) | default |
| realtime-service | 8087 | `product` |
| LiveKit | 7880 (HTTP/WS), 7881 (TCP), 7882 (UDP) | `product` |

## Commands

```bash
make infra-up      # core infra only
make infra-down
make infra-logs

make product-up    # full local product stack (#62) — start here
make product-down
make product-health
make product-demo-seed   # demo personas + friendship (#63)
```

**Beginner guide:** [`docs/operations/getting-started.md`](../docs/operations/getting-started.md)  
**Two-user E2E checklist:** [`docs/operations/workable-product-demo.md`](../docs/operations/workable-product-demo.md)

Copy `.env.example` to `.env` for local app configuration.
