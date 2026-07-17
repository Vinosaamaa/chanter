# Issue #200 Change Log — SEC-19: Env-driven CORS origins

## Problem

Gateway CORS `allowedOrigins` were hardcoded localhost Vite ports in
`application.yml`, so staging/prod required a code change to allow real browser origins.

## Changes

- Removed Spring Cloud Gateway `globalcors` YAML list.
- Added `chanter.cors.allowed-origins` (bound from `CHANTER_CORS_ORIGINS`, comma-separated)
  with local Vite defaults when unset.
- `CorsConfig` / `CorsProperties` register a reactive `CorsWebFilter` so list binding splits
  origins correctly (a single YAML scalar under `globalcors` was treated as one origin string).
- Documented `CHANTER_CORS_ORIGINS` in `.env.example` and staging deploy secrets table.

## Acceptance

- [x] Origins env-driven via `CHANTER_CORS_ORIGINS`
- [x] Local defaults preserve `localhost` / `127.0.0.1` `:5173` / `:5174`
- [x] Gateway tests: `CorsOriginsSmokeTest` + existing JWT filter tests
- [ ] CI + CodeAnt
- [ ] Browser: sign-in from `:5173` still works after gateway restart

## Verification

```bash
cd backend
unset CHANTER_JWT_SECRET CHANTER_INTERNAL_SERVICE_TOKEN CHANTER_CORS_ORIGINS
mvn -pl gateway-service -am test -Dtest=CorsOriginsSmokeTest,JwtAuthenticationGlobalFilterPublicAuthPathsTest -Dsurefire.failIfNoSpecifiedTests=false

# Preflight against a running gateway (defaults):
curl -si -X OPTIONS http://localhost:8080/actuator/health \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: GET' | head -20
```
