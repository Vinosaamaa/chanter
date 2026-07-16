# Issue #194 Change Log — SEC-14: Pin Docker base image tags

## Problem

Compose and the realtime Dockerfile used floating tags (`postgres:16-alpine`, `redis:7-alpine`, `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre`). Floating tags make builds non-reproducible and can silently pull a regressed or compromised upstream.

## Changes

- `infra/docker-compose.yml`: `postgres:16.14-alpine`, `redis:7.4.9-alpine` (digests matched the previous floating tags on 2026-07-16).
- `infra/docker/realtime-service/Dockerfile`: `maven:3.9.16-eclipse-temurin-21`, `eclipse-temurin:21.0.11_10-jre`.
- Left Redpanda / MinIO / LiveKit pins unchanged (already immutable).
- Added `.github/dependabot.yml` weekly Docker updates for `/infra` and `/infra/docker/realtime-service`.
- Documented upgrade process in `docs/operations/docker-image-upgrades.md`.

## Acceptance

- [x] Compose and Dockerfiles use pinned tags
- [x] Document upgrade process
- [ ] CI still passes on PR
