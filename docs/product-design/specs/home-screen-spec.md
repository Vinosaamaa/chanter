# Learner home screen — canonical spec

**Route:** `/home` (or `/app` default after sign-in)  
**Audience:** Enrolled learners with one or more courses  
**Canonical decisions:** [`../DESIGN-DECISIONS.md`](../DESIGN-DECISIONS.md)  
**Mockups:** `journey-3-home.png`, `journey-2-welcome-joined.png` in [`../mockups/learner-flow/`](../mockups/learner-flow/)

## Layout

Standard app chrome on every home view:

- **Left sidebar** — per [`sidebar-spec.png`](sidebar-spec.png); profile row **flush to bottom edge** (no gap below avatar)
- **Top bar** — per [`topbar-tabs-spec.png`](topbar-tabs-spec.png): breadcrumb `Home`, centered search (**⌘F** focuses; scope = **my enrolled courses** — DESIGN-DECISIONS §2.3), notification bell flush right; **no avatar in top bar**

Main content is a two-column layout: primary column (left) + **Up next** panel (right, ~280px).

## Sections (in order)

### 1. Greeting header

- Time-based salutation: "Good morning / afternoon / evening, {displayName}"
- Subline: current date (e.g. "Friday, July 11")

### 2. Needs-attention row

- **Max 3** compact horizontal alert cards
- Examples: office hours starting soon (with primary **Join**), question answered (link to course Questions tab), new announcement count across courses
- Omit cards when there is nothing to surface (do not show empty placeholders)

### 3. Continue learning

- Section title: **Continue learning**
- **2×2 grid** of course cards (fewer cards when user has &lt;4 courses; single card for new user)
- Each card per [`cards-context-panel-spec.png`](cards-context-panel-spec.png):
  - Course color dot + title + subtitle (cohort · instructor)
  - Progress bar in course accent color + percent
  - Footer: activity summary (e.g. "5 new messages · 1 new resource")
- Clicking a card opens that course's workspace (default tab: Overview or last visited)

### 4. Up next (right panel)

- Section title: **Up next**
- Vertical timeline of cross-course items: office hours, live study rooms, assignment deadlines, server/course events
- Rows use small icon + time + label + optional **Join** / link
- Panel extends to fill available height alongside the course grid

## New-user variant

Same structure, reduced content:

- One alert card (onboarding nudge or next office hours)
- One course card at **0%** progress
- One or two **Up next** rows

Welcome modal (first visit only) overlays center; background is this home layout, slightly dimmed.

## Explicitly NOT on home

| Excluded | Lives instead |
|----------|----------------|
| Announcements feed | Study Server **Community space** (Announcements tab) |
| Recent messages list | **Inbox** + sidebar unread badges |
| Quick links to Resources / Office Hours | **Course workspace** tabs |
| People / roster | Course workspace **People** tab |
| Server picker grid | Sidebar server groups + **+ Join or create** |

## UI rules

- Unread badges: show count only when &gt; 0 (never display "0")
- Home **aggregates across courses**; single-course detail stays in course workspace
- No duplicate profile avatar (sidebar only)

## Implementation notes (for issues / PRD)

- Data: enrollments across all accessible Study Servers, aggregated notifications, calendar/deadline feed
- Empty state (zero courses): prompt **+ Join or create** → paste server invite, or open a Study Server **Discover courses** tab
- Performance: alert row and Up next can be paginated or capped; course grid scales with enrollment count
