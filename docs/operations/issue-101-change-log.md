# Issue #101 — Staging deployment with HTTPS

## Summary

Added staging HTTPS runbook and example Caddyfile for single-VM TLS termination with WebSocket (`/api`) and optional LiveKit path proxy.

### Deliverables
- `docs/operations/staging-deploy.md` — deploy, secrets, smoke checklist
- `infra/staging/Caddyfile.example` — TLS reverse proxy template
- `.env.example` — `CHANTER_PUBLIC_BASE_URL` + staging LiveKit `wss://` notes

### Staging URL
Placeholder hostname `https://staging.chanter.example` until DNS/host is provisioned. No secrets committed.

### Verification
Docs-only + Caddyfile example review; no runtime host in this environment.
