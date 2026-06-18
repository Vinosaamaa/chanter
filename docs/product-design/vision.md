# Chanter Product Vision

This document describes the **finished Education MVP product** — the experience educators and learners should have once vertical slices #12–#24 ship. It complements the [Education MVP PRD](../product/education-mvp-prd.md) with screens, navigation, and user stories.

Last updated: 2026-06-17

## Platform: website first, not a desktop app

Chanter is planned as a **web application** — a React + TypeScript + Vite single-page app you open in the browser (Chrome, Safari, Firefox). That matches how Discord started and how cohort learners already work: no install step, share a link, join from a laptop.

| Delivery | MVP plan | Notes |
|---|---|---|
| **Web app (browser)** | Yes — primary surface | Served as static assets behind the gateway/CDN; realtime over WebSocket; voice over WebRTC/LiveKit in the browser |
| **Desktop app (Electron/Tauri)** | Not planned for MVP | Could wrap the same web client later if educators ask; not a separate codebase |
| **Native mobile (iOS/Android)** | Out of scope for Education MVP | Listed in [PRD out of scope](../product/education-mvp-prd.md); responsive web is the mobile story for now |
| **PWA / installable web** | Possible later | “Add to home screen” without app-store work |

**Why web first:** faster iteration, one deploy for all users, easier SSO and billing for B2B educators, and voice/chat APIs (WebRTC, WebSocket) are browser-native. The mockups in `mockups/` are all **browser UI concepts**, not native window chrome.

## Positioning

Chanter is not a generic Discord clone. It sells to **educators** (bootcamps, cohort courses, tutoring groups) who already use Discord plus extra tools and still struggle with:

- Repeated student questions buried in fast-moving channels
- Manual office-hour queues and TA workload
- No visibility into what learners find confusing
- Uncontrolled bots with broad server access

**Buyer value:** learning operations — AI-grounded answers, support workflows, TA queue, office hours, and an instructor dashboard — in one SaaS product.

## Screen gallery

### Marketing home (public)

![Chanter landing page concept](mockups/landing-page.png)

**Visitor path:** hero value prop → feature bands → pricing → **Create Study Server** or **Sign in**.

**User story:** *As a bootcamp founder, I understand why Chanter beats Discord + a random AI bot, and I start a Study Server.*

### Study Server shell (signed-in core app)

![Chanter app shell concept](mockups/app-shell.png)

**Layout (four columns):**

| Col 1 | Col 2 — channels | Col 3 — conversation | Col 4 — context |
|---|---|---|---|
| Server switcher | `#announcements` `#general` (server-wide) | Realtime chat & threads | AI Study Assistant |
| | `#questions` `#resources` (course) | | TA queue |
| | `> study-room` (voice) | | Course resources |

**User story:** *As a learner, I open my cohort's Study Server and everything feels like Discord — but `#questions` has an AI helper that cites our course materials.*

> **Friends hub** is a separate full-screen view (not shown in the app-shell mockup). Open it from the top bar **Friends** icon — see [Friends hub mockup](#friends-hub--direct-messages) below.

### Friends hub & direct messages

![Friends hub and DM chat concept](mockups/friends-hub-dm.png)

Opened from the **Friends** icon in the Study Server shell (or a global top bar). This is the **social plane** — platform-wide, separate from course Support Questions and TA Queue.

**Layout:**

| Area | Content |
|---|---|
| Left rail | Study Server switcher (same as shell — stay oriented) |
| Friends column | **Online** friends with presence pills; **All** friends; pending requests badge |
| DM panel | Conversation with selected friend; message history; composer; **voice call** button (#32) |

**User stories:**

- *As a learner, I see which classmates are online and DM them without leaving Chanter.*
- *As a user, I accept or decline friend requests before anyone can message me (#15).*
- *As friends, we get live message delivery over WebSocket (#31) instead of refreshing.*

**Implementation path:** REST APIs exist today (#15). Live presence, DM fan-out, and friend voice land in [#31](https://github.com/Vinosaamaa/chanter/issues/31) and [#32](https://github.com/Vinosaamaa/chanter/issues/32) after `realtime-service` — see [social hub architecture](../architecture/social-hub-and-dm-voice.md).

### Sign in and onboarding

![Sign in](mockups/sign-in-onboarding.png)

Global account, email/SSO (#30), invite links, and cohort enrollment paths.

### Study Server home

![Study Server home](mockups/study-server-home.png)

Server picker after auth — courses per Study Server, **Create Study Server** for owners.

### Create Study Server

![Create Study Server](mockups/create-study-server.png)

Owner onboarding wizard — name, icon, default channels (`#announcements`, `#general`, `> study-room`).

### `#questions` with AI citations

![AI support question](mockups/ai-support-question.png)

Learner Support Question → **AI Study Assistant** answer with source cards → **Add to TA Queue** when confidence is low (#16–#19).

### `#resources`

![Course resources](mockups/course-resources.png)

Instructor uploads, folder structure, **approved for AI** badges, learner download/search (#17).

### TA Queue

![TA queue](mockups/ta-queue.png)

Cohort-scoped async queue — TAs pick up items routed from low-confidence AI or learner request (#21).

### Office Hours

![Office hours](mockups/office-hours-voice.png)

Scheduled live voice window per cohort; waiting queue alongside participant grid (#22).

### FAQ approval

![FAQ approval](mockups/faq-approval.png)

Instructor reviews **FAQ candidates** from repeated Support Questions; approved FAQs feed search and AI (#20).

### Channel summary

![Channel summary](mockups/channel-summary.png)

Weekly AI digest of `#questions` activity — top topics, follow-ups, export (#7 epic).

### Instructor dashboard

![Instructor dashboard](mockups/instructor-dashboard.png)

Buyer-facing ops view — unanswered questions, repeated topics, queue load, Office Hours, AI quota (#23).

### Cohort enrollment

![Cohort enrollment](mockups/cohort-enrollment.png)

Instructor enrolls learners, assigns TAs, previews channel access (#13).

### Install AI Study Assistant

![AI assistant install](mockups/ai-assistant-install.png)

Human-in-the-loop grant review — channels, courses, cohorts, resources before install (#18).

### SaaS billing and quotas

![SaaS billing](mockups/saas-billing.png)

Study Server Owner plan tier, AI usage meters, quota warnings (#24).

### Global search

![Global search](mockups/global-search.png)

Search across resources, Approved FAQs, and messages — enrollment-scoped (#17+).

### Friend requests

![Friend requests](mockups/friend-requests.png)

Pending inbox — accept, decline, block — separate from the DM conversation panel (#15, #31).

### Course storefront (later phase)

![Course storefront](mockups/course-storefront.png)

Post-MVP: sell courses inside a Study Server; purchase unlocks cohort enrollment.

> Full visual catalog: [mockups/README.md](mockups/README.md)

### Full click-flow map

![User journey diagram](diagrams/user-journey.drawio.png)

Editable source: [`user-journey.drawio`](diagrams/user-journey.drawio)

## Navigation and user stories

### Top-level journey

```
Marketing home → Sign in → Study Server home → Study Server shell
```

| Step | Screen | User story |
|---|---|---|
| 1 | Marketing home | Educator discovers product and starts trial |
| 2 | Sign in / onboard | User gets a global account; joins via invite or cohort enrollment |
| 3 | Study Server home | Pick a community; owner creates courses and cohorts |
| 4 | Study Server shell | Daily home — channels, chat, voice, context panel |

### From the shell — what clicking does

| Click | Goes to | What happens |
|---|---|---|
| `#questions` | Course channel | Learner posts **Support Question** → AI answers with citations → low confidence routes to **TA Queue** |
| `#resources` | Course channel | Browse/search **Course Resources** scoped to enrollment (#17) |
| `> study-room` | Voice channel | Join/leave voice; see presence (#14); Office Hours reuses transport (#22) |
| **Friends** (top bar) | [Friends hub](#friends-hub--direct-messages) | Friends list, online presence, live DM panel (#15 API; #31 UX) |
| **Dashboard** (instructor) | Instructor dashboard | Unanswered questions, FAQ candidates, queue load, AI usage (#23) |

### `#questions` — primary learning-support surface

| Role | Experience |
|---|---|
| **Learner** | Post Support Question; see AI answer with sources; add to TA queue if AI is unsure |
| **AI Study Assistant** | Answers only from approved resources and FAQs; declines when not confident (#18–#19) |
| **Instructor / TA** | List unanswered questions; approve repeated questions as FAQs (#16, #20) |

**User story:** *As a learner, I ask how to configure Spring Security in `#questions` and get a cited answer from week-3 slides — or a clear handoff to a human.*

### Instructor dashboard — buyer-facing value

| Widget | Purpose |
|---|---|
| Unanswered Support Questions | See backlog without reading every channel |
| Repeated questions / FAQ candidates | Reduce repetitive TA work (#20) |
| TA Queue load | Async human help demand (#21) |
| Office Hours schedule & load | Live support windows per cohort (#22) |
| Channel summaries | Weekly digest of `#questions` activity |
| AI usage vs plan quota | SaaS cost control (#24) |

**User story:** *As an instructor, I open the dashboard Monday morning and see twelve people asked about the same auth topic — I approve one FAQ and stop answering it manually.*

### Friends hub — social layer (parallel to learning)

| Feature | Slice |
|---|---|
| Friend request send/accept/decline | #15 (API done) |
| Friends list + online presence | #31 |
| Live DM conversation panel | #31 |
| DM voice call | #32 |

Social messaging is **platform-wide** and intentionally separate from Support Questions and TA Queue.

## Channel model (defaults)

**Study Server** (created in #12):

- `#announcements` (text)
- `#general` (text)
- `> study-room` (voice)

**Course** (created in #13):

- `#announcements` (text)
- `#questions` (text) — support questions & AI
- `#resources` (text) — resource discussion

Optional **Cohort Channel** — instructor opt-in private space per cohort.

Access is scoped by **Cohort Enrollment** and role (Owner, Instructor, TA, Learner). See [CONTEXT.md](../../CONTEXT.md).

## Built today vs target UI

| Capability | Backend / API | Target product UI |
|---|---|---|
| Create Study Server | #12 done | Demo form → full server picker |
| Course, cohort, enroll | #13 done | Course cards in server home |
| Voice presence | #14 done | Voice grid in `> study-room` |
| Friends & DMs | #15 done | Friends hub (#31 for live UX) |
| Support questions | #16 in PR | Realtime `#questions` chat |
| Course resources | #17 planned | `#resources` panel + uploads |
| AI Study Assistant | #18–#19 planned | Context panel + citations |
| TA queue & office hours | #21–#22 planned | Queue UI + scheduled voice |
| Instructor dashboard | #23 planned | Dedicated dashboard route |

Today's `frontend/src/App.tsx` is a **vertical-slice demo** (forms and buttons proving APIs). The mockups in `mockups/` show the **production shell** once navigation, WebSocket chat, and dashboard routes land.

## Later phases (post-MVP)

Dashed boxes in the [user journey diagram](diagrams/user-journey.drawio.png):

- **Course storefront** — sell courses inside a Study Server; purchase unlocks cohort enrollment
- **Live Class video** — built-in teaching rooms, recordings, transcripts
- **Agent marketplace** — install third-party agents with billing and safety governance
- **Organization SSO** — enterprise tier

## See also

- [Product design showcase README](README.md) — index of all assets
- [Education MVP PRD](../product/education-mvp-prd.md) — full requirements
- [Issue breakdown](../issues/education-mvp-issue-breakdown.md) — implementation slices
- [Interactive screen tour](interactive/README.md) — clickable walkthrough in Cursor
