# Issue #181 change log — Reject default JWT and internal-service secrets (SEC-04)

**Branch:** `feature/181-reject-default-secrets`  
**Parent:** #180 · Finding: `docs/operations/codebase-review-2026-07-16.md` § SEC-04

## Summary

Stop shipping usable JWT / internal-service secrets as working defaults. Validation now rejects the known in-git example values (not just length), compose no longer falls back to them, and `.env` is not silently copied from `.env.example` without opt-in. Local bring-up uses `make product-env` to generate unique secrets.

## Changes

| Area | Change |
|------|--------|
| `.env.example` | Empty `CHANTER_JWT_SECRET=` / `CHANTER_INTERNAL_SERVICE_TOKEN=` placeholders |
| `infra/docker-compose.yml` | Removed `:-chanter-local-dev-jwt-secret-32bytes!!` fallback on realtime-service |
| `scripts/product/lib.sh` | Reject known defaults; refuse auto-copy unless `CHANTER_ALLOW_ENV_EXAMPLE_COPY=1` |
| `scripts/product/init-env.sh` | New helper: create/refresh `.env` with random secrets |
| `Makefile` | `make product-env`; length + default rejection in require macros; `infra-up` requires `.env` |
| `JwtTokenService` | Constructor rejects the historical default JWT string |
| Docs | `getting-started.md`, `workable-product-demo.md` use `make product-env` |

## Tests

```bash
./scripts/product/lib.test.sh
# or
make product-test
```

Added assertions:

- known default JWT secret → fail with "known default"
- known default internal token → fail with "known default"
- missing `.env` without opt-in → fail pointing at `make product-env`

## Local bring-up (documented)

```bash
make product-env
make product-supervise   # or make product-up
make product-health
make product-demo-seed
```

Existing `.env` files that still contain the old example secrets will fail until refreshed with `make product-env`.
