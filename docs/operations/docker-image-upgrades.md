# Docker image upgrades

**Last updated:** 2026-07-16  
**Related:** SEC-14 / #194 · inventory in `infra/docker-compose.yml` and `infra/docker/`

## Policy

- Pin every Compose `image:` and Dockerfile `FROM` to an **immutable version tag** (patch/release), not a floating major/minor alias (`16-alpine`, `7-alpine`, `21-jre`, `3.9-eclipse-temurin-21`).
- Prefer the same style already used for Redpanda, MinIO, and LiveKit (versioned tags). Digest pins (`@sha256:…`) are optional and stronger; use them when you need bit-for-bit reproducibility.
- Do not float tags back after Dependabot bumps.

## Current inventory

| Image | Pin | Where |
|-------|-----|-------|
| Postgres | `postgres:16.14-alpine` | `infra/docker-compose.yml` |
| Redis | `redis:7.4.9-alpine` | `infra/docker-compose.yml` |
| Redpanda | `docker.redpanda.com/redpandadata/redpanda:v24.2.4` | `infra/docker-compose.yml` |
| MinIO | `minio/minio:RELEASE.2024-12-18T13-15-44Z` | `infra/docker-compose.yml` |
| LiveKit | `livekit/livekit-server:v1.13.1` | `infra/docker-compose.yml` |
| Maven (build) | `maven:3.9.16-eclipse-temurin-21` | `infra/docker/realtime-service/Dockerfile` |
| JRE (runtime) | `eclipse-temurin:21.0.11_10-jre` | `infra/docker/realtime-service/Dockerfile` |

Keep the realtime runtime on a **non-Alpine** Temurin JRE (Dockerfile uses `groupadd` / `useradd`).

## How to upgrade

1. Prefer Dependabot PRs from `.github/dependabot.yml` (`docker` ecosystem for `/infra` and `/infra/docker/realtime-service`).
2. Manual: look up the new patch tag on Docker Hub / vendor docs, edit the pin, open a PR.
3. Verify before merge:
   - `docker compose -f infra/docker-compose.yml --profile product config --quiet`
   - `make product-up` (or supervise) and `make product-health`
   - For realtime image changes: rebuild with compose `--build` / image rebuild, then health-check `:8087`
4. Postgres: stay on major **16** for existing local volumes; major bumps need dump/restore.
5. Redis: stay on major **7** unless intentionally migrating; keep `REDIS_PASSWORD` required.

## CI

The backend CI job already runs `docker compose … config --quiet`, which catches invalid image references after a pin change.
