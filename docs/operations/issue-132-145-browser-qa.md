# Browser QA — Issues #132–#145 (2026-07-15)

**Stack:** local `product-supervise` + `product-demo-seed`  
**Personas:** `dev-demo-owner@chanter.local` / `dev-demo-learner@chanter.local` (password `chanter-dev-demo`)  
**Server:** `895ee15a-6b53-44c3-93f9-07af131c72e8` · **Course:** `daff6b40-999f-4d5f-b04c-8798a0fe94da`

## Startup blockers fixed before UI QA

| Bug | Impact | Fix |
|-----|--------|-----|
| `HttpNotificationClient` dual constructors → Spring `No default constructor found` | `message-service` would not boot | `@Autowired` on production constructor (PR #163) |
| Missing `chanter_notification` on reused Postgres volume | `notification-service` would not boot | `product_ensure_databases` in product infra (PR #163) |

## UI matrix

| Issue | Surface | Result |
|------:|---------|--------|
| 132 | Cohort context across course tabs | PASS |
| 133 | Shell search / account / join-create | PASS |
| 134 | Course Resources + AI install | PASS |
| 135 | Office Hours schedule / Start | PASS |
| 136 | People roster / enroll / TA | PASS |
| 137 | Course Chat + voice channels | PASS |
| 138 | Community Discover catalog | PASS |
| 139 | Create/invite/publish controls | PASS |
| 140 | Community Events + RSVP | PASS |
| 141 | Announcements / Members / Invite | PASS |
| 142 | Home + Course Overview aggregates | PASS |
| 143 | Inbox + unread bell | PASS |
| 144 | Cross-course Calendar Join/Going | PASS |
| 145 | Owner billing / usage settings | PASS |

**UI bugs found:** none after startup fixes.

## Content created during QA

- Office Hours: Wed Jul 15, 2026 · 8:00–9:00 AM (Demo Cohort)
- Announcement: “QA Test Announcement”
- Event: “QA Test Event” · Sat Jul 18, 2026 · 2:00–4:00 PM (hub-wide)

## Learner spot-check

- Home / Calendar / Inbox load; unread notifications from owner-created content
- `/app/settings/billing` redirects non-owners to home
