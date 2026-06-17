# Chanter Infrastructure

Local development dependencies via Docker Compose.

## Services

- PostgreSQL 16 (`localhost:5432`)
- Redis 7 (`localhost:6379`)
- Redpanda (`localhost:19092`)
- MinIO (`localhost:9000`, console `9001`)

## Commands

```bash
make infra-up
make infra-down
make infra-logs
```

Copy `.env.example` to `.env` for local app configuration.
