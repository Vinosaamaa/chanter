# Chanter Backend

Java 21 + Spring Boot 3 microservices (Maven multi-module).

## Implemented modules

- `common` — shared contracts
- `gateway-service` — API gateway (port 8080)
- `auth-service` — auth bootstrap (port 8081)
- `community-service` — Study Server creation and lookup (port 8082)

## Commands

```bash
make backend-test
make backend-auth    # terminal 1
make backend-community # terminal 2
make backend-gateway  # terminal 3
```

## Planned services

Other directories under `backend/` are reserved service boundaries documented in `plan.md`.
