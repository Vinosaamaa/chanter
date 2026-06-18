# Chanter Backend

Java 21 + Spring Boot 3 microservices (Maven multi-module).

## Implemented modules

- `common` — shared contracts
- `gateway-service` — API gateway (port 8080)
- `auth-service` — auth bootstrap (port 8081)
- `community-service` — Study Servers, courses, enrollment, voice presence (port 8082)
- `message-service` — friend requests, DMs, support questions (port 8083)
- `media-service` — course resource uploads and downloads (port 8084)

## Commands

```bash
make backend-test
make backend-auth       # terminal 1
make backend-community  # terminal 2
make backend-message    # terminal 3
make backend-media      # terminal 4
make backend-gateway    # terminal 5
```

## Planned services

Other directories under `backend/` are reserved service boundaries documented in `plan.md`.
