# Chanter Backend

Java 21 + Spring Boot 3 microservices (Maven multi-module).

## Implemented modules

- `common` — shared contracts
- `gateway-service` — API gateway (port 8080)
- `auth-service` — auth bootstrap (port 8081)

## Commands

```bash
make backend-test
make backend-auth    # terminal 1
make backend-gateway  # terminal 2
```

## Planned services

Other directories under `backend/` are reserved service boundaries documented in `plan.md`.
