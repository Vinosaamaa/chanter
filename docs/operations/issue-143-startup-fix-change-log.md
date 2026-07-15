# Issue #143 follow-up — notification stack startup fixes (browser QA)

## Summary

During browser QA for issues #132–#145, `message-service` and `notification-service` failed to boot on a reused local Postgres volume.

## Fixes

1. **`HttpNotificationClient` (message-service)** — Spring could not choose a constructor when both the production and package-private test constructors existed (`No default constructor found`). Marked the properties constructor with `@Autowired`.
2. **`product_ensure_databases`** — Postgres init SQL already creates `chanter_notification`, but init only runs on first volume create. Product infra startup now ensures all service databases exist so older volumes pick up DBs added after the volume was first created (including `chanter_notification` from #143).

## Verify

```bash
make product-down && make product-supervise
# or, with infra already up:
# rebuild message-service, then start message-service + notification-service
curl -sS http://localhost:8083/actuator/health
curl -sS http://localhost:8089/actuator/health
```
