# Public beta launch checklist (#104)

Sign-off checklist before inviting external beta users. Staging hostname placeholder: `https://staging.chanter.example` (see [`staging-deploy.md`](staging-deploy.md)).

## Scope (beta)

**In scope**
- Study Server / Course shell (UI v2)
- Chat, Questions + Ask AI (RAG; optional LLM), Resources, Office Hours
- Friends / DM, Community events RSVP, Inbox, Calendar, Teaching, billing plan UI
- Local + staging deploy paths; production auth hooks (#102)

**Out of scope / known limitations**
- `course-storefront.png` commerce storefront — **post-MVP**
- Marketing header friends/notification badges — post-launch polish
- Multi-region HA / full production SRE — later

## Security

| Item | Owner | Status | Notes |
|------|-------|--------|-------|
| Branch protection / PR CI green | | ☐ | Never push to `main` |
| Secrets only in host env (not git) | | ☐ | JWT, internal token, OAuth, SMTP |
| Auth rate limits enabled | | ☐ | #102 |
| Email verification on staging | | ☐ | `CHANTER_AUTH_REQUIRE_EMAIL_VERIFICATION=true` |
| HTTPS + HSTS via Caddy | | ☐ | #101 |
| Dependency / SAST review (CodeAnt) | | ☐ | Latest release PR |

## Backup & recovery

| Item | Owner | Status | Notes |
|------|-------|--------|-------|
| Postgres backup schedule documented | | ☐ | Volume snapshots or `pg_dump` |
| Restore drill recorded | | ☐ | |
| MinIO / media backup | | ☐ | |

## Monitoring & support

| Item | Owner | Status | Notes |
|------|-------|--------|-------|
| Gateway `/actuator/health` probed | | ☐ | |
| Support email published | | ☐ | e.g. `support@chanter.example` |
| On-call / escalation contact | | ☐ | |

## Legal placeholders

| Item | Owner | Status | Notes |
|------|-------|--------|-------|
| Terms page (`/terms`) reviewed | | ☐ | Placeholder OK for closed beta |
| Privacy policy placeholder linked | | ☐ | Add `/privacy` or footer note |
| Beta user agreement / data notice | | ☐ | |

## Product readiness

| Item | Owner | Status | Notes |
|------|-------|--------|-------|
| `make product-e2e-critical` green | | ☐ | #103 |
| `make product-e2e` smoke on staging host | | ☐ | Optional |
| Demo seed credentials rotated for shared staging | | ☐ | Or disable seed |
| AI Study Assistant grants verified on demo course | | ☐ | |
| Landing `/` marketing preview approved | | ☐ | TA queue / stats / Join Queue |

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Eng | | | ☐ |
| Product | | | ☐ |
| Security (light) | | | ☐ |

## Related docs

- [`staging-deploy.md`](staging-deploy.md)
- [`getting-started.md`](getting-started.md)
- [`workable-product-demo.md`](workable-product-demo.md)
- [`no-dead-controls-inventory.md`](no-dead-controls-inventory.md)
- [`issue-104-change-log.md`](issue-104-change-log.md)
