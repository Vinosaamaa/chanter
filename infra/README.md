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

make product-up    # full local product stack (#62)
make product-down
make product-health
```

Copy `.env.example` to `.env` for local app configuration.
