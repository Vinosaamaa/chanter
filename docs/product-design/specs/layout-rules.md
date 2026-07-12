# Layout & navigation rules (v2 mockups)

Canonical rules for regenerating learner and owner mockups. **Full decision record:** [`DESIGN-DECISIONS.md`](../DESIGN-DECISIONS.md). Use with component specs in this folder.

## Sidebar active states

| Route / context | What is highlighted |
|-----------------|---------------------|
| Learner home (`/home`) | **Home** nav — filled indigo pill |
| Course workspace (`/courses/{id}/…`) | **Course row** — accent bar + tint (per `sidebar-spec.png`) |
| Server community (`/hub/{server}/…`) | **Server group header** (e.g. SPRING BOOTCAMP HUB) — expanded + tint; **Home is not active** |

Profile row: **flush to bottom** of sidebar — no gap below avatar.

**Chanter logo (sidebar top):** purple/indigo rounded icon with **white speech bubble + two dots** + wordmark **Chanter** — per [`sidebar-spec.png`](sidebar-spec.png). Do not use a letter “C” or generic blue square logo.

## App chrome stacking (main pane)

The **top bar lives in the main (right) pane only** — it does **not** span over the left sidebar. The sidebar is full-height from logo to profile; the right panel has its own top bar row above content.

```
┌──────────┬────────────────────────────────────────────────┐
│ Sidebar  │ TOP BAR (right pane only): breadcrumb · search │
│ (logo)   │            (⌘F) · bell (×1)                    │
│ Home     ├────────────────────────────────────────────────┤
│ Teaching │ page header / course header / hub header       │
│ …        │ tab strip (if applicable)                      │
│ courses  ├────────────────────────────────────────────────┤
│ + Join   │ tab content                                    │
│ profile  │                                                │
└──────────┴────────────────────────────────────────────────┘
```

**Mockup framing:** Show UI inside a **browser window** (Safari/Chrome chrome, address bar e.g. `app.chanter.io/home`) — match learner mockups in [`../mockups/learner-flow/`](../mockups/learner-flow/).

One top bar per screen — **never duplicate** below course tabs.

- **Single notification bell** — only in the global top bar (no second bell in course header or mid-page strip).
- Breadcrumb + search live in the **top bar row**, not between tabs and chat content.

**Mockup generation:** Always include the **complete top bar** (breadcrumb · centered search with **⌘F** · single bell) in every PNG. Modals dim the shell behind but the top bar must remain visible and uncropped.

### Course workspace breadcrumb (cohort)

| User | Top bar breadcrumb |
|------|-------------------|
| Learner, one cohort | `Spring Bootcamp Hub / CS 101` |
| Instructor, 2+ cohorts | `Spring Bootcamp Hub / CS 101 / Spring 2026 ▾` |

- **Cohort dropdown** on the **last breadcrumb segment** (`Spring 2026 ▾`) — plain text + chevron, **no pill/circle border**.
- **Course header subtitle** shows the same cohort (`Spring 2026 ▾ · Dr. Alex Johnson`) — **one picker state**, synced; also text + chevron only.
- Learner with one cohort: no `▾`; optional static third segment or cohort only in subtitle.
- Switching cohort reloads tab content for that cohort; **stay on same tab**.

## Course workspace chrome (all `journey-4*` tabs)

**Every** course workspace screen must show the same header block **above** tab content — Chat, Resources, Overview, etc. Do not omit on any tab.

```
┌─────────────────────────────────────────────────────────────┐
│  ● CS 101 — Intro to Computer Science    [Study Room · live] [+ Invite] │
│    Spring cohort · Dr. Alex Johnson                          │
│  Overview | Chat | Questions | Resources | Office Hours | People │
│  ─────────  (active tab: indigo underline)                   │
└─────────────────────────────────────────────────────────────┘
│  … tab-specific content …                                    │
```

- **Course title row:** course color dot + full course name (always visible).
- **Subtitle:** cohort · instructor name.
- **Optional right actions:** live study room pill, Invite (may vary by tab).
- **Tab strip:** directly below title block on **every** course tab mockup.
- Breadcrumb + search + **one** notification bell sit in the **global top bar above** this course header — not repeated below tabs.

## Search (route-scoped — not Discord server-wide)

- **Every screen** has the top-bar search input — **no** separate search-overlay journey mockup.
- **⌘F** focuses the search field (not ⌘K). It does **not** change scope — navigation does.
- **Default scope = current route/tab** — see [`DESIGN-DECISIONS.md`](../DESIGN-DECISIONS.md) §2.3 (Discord contrast + route table).
- **Course workspace optional narrowing:** active tab may show a **muted scope preview** in the search bar (e.g. `in:#general ` on Chat, `@questions ` on Questions). User can clear to search whole course.
- Placeholder text changes per route; scope preview tokens on course Chat / Questions mockups when relevant.

## Course workspace — Chat tab (`4b`)

**Two-tone layout** (reference: Slack) — channel panel and chat panel are **two separate bordered cards** side by side:

```
┌─────────────────┐  ┌────────────────────────────────────┐
│ Channel panel   │  │ Chat panel                         │
│ darker tone     │  │ lighter background                 │
│ 1px border      │  │ 1px border, rounded corners        │
│ rounded corners │  │ messages — NO lines between msgs   │
│ scroll          │  ├────────────────────────────────────┤
│                 │  │ composer (this panel only)           │
└─────────────────┘  └────────────────────────────────────┘
```

- **Outside** (area around the two panels): **darkest** tone.
- **Channel panel:** bordered card; interior **lighter than outside**, **darker than chat panel**.
- **Chat panel:** bordered card; interior **lightest of the three** (still dark-theme).
- **Messages:** natural spacing only — no divider lines between messages.
- **Composer:** bottom of chat panel only.

Three-column layout sits **below** course title + tab strip:

- **Add channel:** `+` on **CHANNELS** and **VOICE** section headers — **instructor + owner** only. **No** dashed “Add channel” at panel bottom. Learners: read-only channel list.

## Course workspace — Resources tab (`4d`, learner)

Follow learner Resources layout (reference: week-folder list). **Always include course chrome** (title + tabs) above this content.

- **Learner view:** browse and download only — **no Upload** button.
- Upload / organize folders: **instructor / owner** (`owner-flow/`).
- **No right panels** — no AI Study Assistant card, no Recently Viewed, no About/Upcoming.

**Resources content (full width below tabs):**

1. **Search** — `Search resources…` input, full width or leading.
2. **Filter chips** — `All` (active), `Slides`, `Recordings`, `Assignments`.
3. **Week folders** — collapsible sections, e.g.:
   - *Week 1 — Foundations* → Lecture PDF (AI-approved badge, download), Lecture recording (AI-approved, download), Problem Set (Due soon badge, download).
   - *Week 2 — Recursion & Complexity* → Slides (AI-approved, New dot), external link row.
4. Row anatomy: file-type icon, title, metadata (size / duration / due), badges, download or external-link action at row end.

Deadlines also surface on **Home** (Up next) and **Overview** — not in a Resources sidebar.

## Course workspace — Office Hours tab (`4e`)

**Open voice hours** (Discord-style) — not a Zoom grid or numbered admit queue.

- Instructor schedules a cohort window (e.g. weekly); learners join during that window.
- **Join to listen** — mic off by default; no auto-join audio on tab switch.
- **Raise hand** to request speak permission; host calls on raised hands.
- Single bordered card below tabs (no separate queue sidebar, no video tile grid).

**In-session layout:**

1. Header — LIVE badge, time window, subtitle: *Open voice session — join to listen, raise hand to speak*
2. **Speaking now** — host (+ TA if present), active speaker ring
3. **Listening (N)** — enrolled learners listening (headphone/muted state)
4. **Hands raised** — learners waiting to be called on
5. Control bar — Connected · Listening, mic toggle, **Raise Hand**, Leave

**Not** on this tab: per-learner admit-next queue, 12-person video grid, private breakout rooms.

## Course workspace tabs (all under `journey-4*`)

| ID | File | Tab |
|----|------|-----|
| 4a | `journey-4a-course-overview.png` | Overview |
| 4b | `journey-4b-course-chat.png` | Chat |
| 4c | `journey-4c-course-questions.png` | Questions (+ AI citations) |
| 4d | `journey-4d-course-resources.png` | Resources |
| 4e | `journey-4e-course-office-hours.png` | Office Hours |
| 4f | `journey-4f-course-people.png` | People |

## Server community tabs (all under `journey-5*`)

| ID | File | Tab |
|----|------|-----|
| 5a | `journey-5a-community-announcements.png` | Announcements |
| 5b | `journey-5b-community-lounge.png` | Lounge |
| 5c | `journey-5c-community-events.png` | Events (list) |
| 5c-detail | `journey-5c-community-events-detail.png` | Events — detail modal |
| 5d | `journey-5d-community-members.png` | Members |
| 5e | `journey-5e-community-discover-courses.png` | Discover courses |

**All `journey-5*` screens** use the same five community tabs:

`Announcements · Lounge · Events · Discover courses · Members`

**Lounge (`5b`):** two-tone channel + chat panels (same pattern as course Chat `4b`) — `# lounge`, `# general`, `# introductions`, `# off-topic`, optional **Community Lounge** voice. **No** About / Upcoming events right panel.

**Events (`5c`):** full-width event card list — **Upcoming / Past / Going** filters, Sign up / Add to calendar / Interested on cards. **No** right panel. Click card → **detail modal** (`journey-5c-community-events-detail.png`): description, host, Going ✓, Add to calendar, Cancel RSVP.

**Members (`5d`):** full-width roster — Staff / Learners, search, Online / Staff / Learners filters. **No** right panel.

**Discover courses (`5e`):** **2×2 course card grid** (not full-width rows). Filter chips: All / Enrolled / Open / Opening soon. Courses **on this Study Server only**. Hub header course count must match sidebar (e.g. 3 courses). **No** right panel.

**Inbox (`6`):** two-tone notification list + thread; Inbox nav active; All / Mentions / Announcements.

**Friends (`7`):** two-tone friends list + DM; Pending requests badge.

**Calendar (`8`):** month grid + selected-day agenda + upcoming week — filter chips **All · Office hours · Events · Deadlines · Going**. Cross-course read-only aggregate; tap item → deep-link. See [`journey-8-calendar.png`](../mockups/learner-flow/journey-8-calendar.png) and DESIGN-DECISIONS §2.6.

Right context panel (About, Upcoming events) is appropriate on **Announcements** (`5a`) only — not on Lounge, Events, Discover courses, Members, or learner Resources.

## Learner journey index (complete)

| ID | File | Screen |
|----|------|--------|
| 1 | `journey-1-signup-invite.png` | Sign up via invite |
| 2 | `journey-2-welcome-joined.png` | Welcome modal |
| 3 | `journey-3-home.png` | Home — see [`home-screen-spec.md`](home-screen-spec.md) |
| 6 | `journey-6-inbox.png` | Unified inbox |
| 7 | `journey-7-friends-dm.png` | Friends + DM (+ requests) |
| 8 | `journey-8-calendar.png` | Calendar — cross-course aggregate |

## Owner / instructor (same shell — see DESIGN-DECISIONS.md)

- **Teaching** nav item (between Home and Inbox) when user has instructor/owner role.
- **Settings** modal from profile/gear; billing under Study server settings.
- Owner actions live **in context** on course/community tabs + Teaching dashboard summary.
