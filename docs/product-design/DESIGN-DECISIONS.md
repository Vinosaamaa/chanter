# UI redesign decisions (v2) — canonical reference

**Status:** Learner + owner mockups in [`mockups/learner-flow/`](mockups/learner-flow/) and [`mockups/owner-flow/`](mockups/owner-flow/).  
**Design:** [#114](https://github.com/Vinosaamaa/chanter/issues/114) (closed). **Implementation:** epic [#115](https://github.com/Vinosaamaa/chanter/issues/115), slices [#116–#128](https://github.com/Vinosaamaa/chanter/issues/116).  
**Supersedes:** Discord-style four-column channel tree in [`../mockups/`](../mockups/) for new UI work.

**For agents:** Implement and generate mockups to match this document + [`specs/layout-rules.md`](specs/layout-rules.md) + PNGs in [`mockups/learner-flow/`](mockups/learner-flow/) and [`mockups/owner-flow/`](mockups/owner-flow/). When in doubt, read this file first.

---

## 1. Core principles

| Principle | Decision |
|-----------|----------|
| **Course-first** | Primary navigation is **course workspace tabs**, not a nested `#channel` tree in the sidebar. |
| **One shell** | Learners, instructors, TAs, and owners use the **same layout** (sidebar + top bar + main pane). Extra actions are **role-gated**, not a separate admin app. |
| **Cohort-scoped learning** | Chat, Questions, People, Office Hours, TA queue are **per cohort**. Resources are mostly **per course** with optional cohort folders. |
| **Community vs course** | **Study Server community** (Announcements, Lounge, …) is separate from **course workspace** (Overview, Chat, …). |
| **Social vs learning** | Friends/DMs are global; Support Questions, TA Queue, Office Hours never live in DMs. See [`../visibility-and-social-model.md`](../visibility-and-social-model.md). |

---

## 2. App shell (all signed-in users)

### 2.1 Left sidebar

```
Chanter (logo)
├── Home
├── [Teaching]          ← only if user has instructor/owner role on any server
├── Inbox
├── Calendar            ← cross-course aggregate (office hours, events, deadlines)
├── Friends
├── SPRING BOOTCAMP HUB ▾   ← server group; highlighted on community routes
│   ├── CS 101 — Intro to CS
│   ├── MATH 201 — …
│   └── …
├── UNIVERSITY STUDY CLUB ▾
│   └── …
├── + Join or create
└── [Profile row]       ← flush to bottom, no gap below avatar
```

- **Logo:** purple/indigo rounded icon, **white speech bubble + two dots** + wordmark **Chanter** — not letter “C”.
- **Course rows:** show enrollments as `Course name` (optionally `— Cohort label` when multiple).
- **+ Join or create:** join another server (invite code) **or** create study server (owner path).

### 2.2 Top bar (right pane only — never over sidebar)

| Element | Rule |
|---------|------|
| **Placement** | Top bar row sits **only above the main (right) pane** — sidebar is full-height; logo at sidebar top, **no** top bar spanning the whole window |
| **Breadcrumb** | Context path; see §4 for course cohort segment |
| **Search** | Centered input; hint **⌘F** (not ⌘K) |
| **Bell** | **One** notification bell, flush right |
| **Avatar** | **Not** in top bar — profile only in sidebar bottom |

**Mockup framing:** Render inside a **browser window** (address bar, e.g. `app.chanter.io/…`) — same as approved learner journey mockups.

**Mockup rule:** Every screen must show the **complete top bar** in the right pane — breadcrumb · search · bell — above hub/course headers. Never crop it; never draw it over the sidebar.

### 2.3 Search (route-scoped — not Discord server-wide)

**Keep the search bar on every screen** — same chrome, same **⌘F** to focus. Scope and placeholder **change automatically when you navigate**; results never leak outside enrollment/role.

#### Not Discord’s model

| | **Discord** | **Chanter (v2)** |
|---|-------------|------------------|
| Default scope | Whole **server** (all channels) | Current **route / tab** only |
| Switch channel/tab | Search still spans server | Scope **follows navigation** — no extra step |
| **⌘F** | Locks search to **current channel** | **Focuses** the search field only — scope already set by route |

**Decision:** You do **not** press ⌘F to “lock in” the current tab. Navigation sets scope; ⌘F just focuses the bar.

- **No** separate ⌘K full-screen search journey mockup.
- **No cross-server / platform-wide search in MVP** (deferred post-MVP).

#### Scope by route (default)

| Route | Search scope | Placeholder (example) |
|-------|--------------|------------------------|
| **Home** | **My learning** — enrolled courses only: course names, messages/resources/questions in those courses, hub announcements & events for hubs you belong to | `Search your courses…` |
| **Teaching** | Courses you instruct: open questions, TA queue items, resources, roster names | `Search your teaching…` |
| **Inbox** | Unified inbox threads + message bodies | `Search inbox…` |
| **Friends** | Friends list by name + **your DM conversations** (not course channels) | `Search friends and messages…` |
| **Calendar** | Events, office hours, deadlines in visible range (when Calendar ships) | `Search calendar…` |
| **Course workspace** | That **course** (cohort-scoped for chat/questions/people; course-scoped for resources) | `Search this course…` |
| **Community hub** | That **hub + active tab** (e.g. Discover → courses in hub; Lounge → lounge messages) | `Search this hub…` |

**Home is not global discovery.** It does not search all Study Servers on the platform or courses you are not enrolled in. To browse/join courses → **Discover courses** tab in a hub.

**Friends is not course search.** Course chat stays in course workspace; Friends search covers social graph + DMs only.

#### Optional narrowing in course workspace (scope preview hints)

Within **course workspace**, the active tab may show a **scope preview** inside the search field — a muted prefix/token before the cursor. User can clear or edit it to widen back to whole-course search.

| Course tab | Default scope | Scope preview in search bar (example) |
|------------|---------------|--------------------------------------|
| **Overview** | Whole course | *(none — `Search this course…`)* |
| **Chat** | Current channel (or all course channels if no channel selected) | `in:#general ` |
| **Questions** | Support questions in this cohort | `@questions ` |
| **Resources** | Course resources | `in:resources ` |
| **Office Hours** | Office hours sessions / transcripts for cohort | `in:office-hours ` |
| **People** | Roster in this cohort | `in:people ` |

- Preview is **optional UX** — typing without clearing still searches the tab’s default scope.
- Clearing the token searches the **whole course** (wider).
- Mockups: show muted scope prefix on Chat and Questions tabs when illustrating course search.

### 2.4 Settings (Discord-style)

- Click **profile** (sidebar bottom) or **gear** (owner/admin) → **Settings modal** overlay (same shell behind), not a separate app layout.
- **User:** profile, notifications, appearance.
- **Study server** (owner/admin): General, Members & roles, **Plan & Billing**, integrations.
- Billing mockup: `owner-flow/saas-billing.png` → **Settings → Study server → Plan & Billing**.

### 2.5 Active states

| Context | Highlight |
|---------|-----------|
| `/home` | **Home** nav — indigo pill |
| Course workspace | **Course row** in sidebar — accent bar + tint |
| Server community | **Server group header** expanded + tint; Home **not** active |
| `/calendar` | **Calendar** nav — filled indigo pill |

### 2.6 Calendar (`journey-8`)

**Route:** `/calendar` · **Audience:** all signed-in users (learners + instructors use same view).

**Purpose:** Read-only **cross-course aggregate** — office hours, hub/course events you’re **Going** to, assignment deadlines. Same items surfaced on Home **Up next**; Calendar is the full schedule view. Tap an item → deep-link (course Office Hours tab, community event, Resources assignment, etc.).

**Chrome:** standard shell — **Calendar** nav active; top bar breadcrumb `Calendar`; search `Search calendar…` (**⌘F**); bell; no avatar.

**Layout:** month grid (left/main) + **selected-day agenda** + **Upcoming this week** (right panel ~280px).

| Area | Content |
|------|---------|
| **Toolbar** | Month prev/next · **Today** · filter chips **All · Office hours · Events · Deadlines · Going** |
| **Month grid** | Dots on days by type (blue OH, purple events, yellow deadlines, green Going) · today highlighted |
| **Right panel** | Selected day rows (time · title · course/hub · **Join** / **Going ✓**) · upcoming week list |

**Not here:** creating/editing office hours or events (those live on course **Office Hours** tab or community **Events** tab).

**Mockup:** [`mockups/learner-flow/journey-8-calendar.png`](mockups/learner-flow/journey-8-calendar.png)

---

## 3. Global nav: Teaching dashboard

**Home (option A — locked):** **Same learner home for everyone** — Continue learning, needs attention, Up next. Instructors/owners do **not** get a separate Home layout; teaching ops live on **Teaching** + course tabs.

- **Learners / TA-only:** sidebar = Home · Inbox · Calendar · Friends (no Teaching).
- **Instructor / server owner:** insert **Teaching** between **Home** and **Inbox**.
- **Teaching** opens cross-course ops dashboard: unanswered questions, TA load, office hours today, AI quota, FAQ candidate counts — **links** into course tabs (not duplicate UIs).
- **TA:** no separate TA dashboard; work in **course → Questions** tab only.
- **Owner** sees server-wide aggregates on Teaching dashboard + **Settings** for billing/members.

---

## 4. Course workspace

### 4.1 Entry and cohort switching

- **Click course in sidebar** → enter course workspace immediately (default cohort).
- **Do not** show a mandatory cohort picker page before Overview.
- **Default cohort:** last visited → active cohort → only enrollment (learners almost always one).

**Breadcrumb (top bar):**

| User | Pattern |
|------|---------|
| Learner, one cohort | `Spring Bootcamp Hub / CS 101` |
| Instructor, 2+ cohorts | `Spring Bootcamp Hub / CS 101 / Spring 2026 ▾` |

- **Dropdown on last segment** (`Spring 2026 ▾`) switches cohort — GitHub branch / repo pattern.
- **Do not** put cohort switcher on `CS 101` segment (course ≠ cohort).
- **Style:** plain text + chevron only — **no** pill border, circle, or outlined button around the cohort segment (in breadcrumb or course subtitle). Match learner mockups: subtle link-like dropdown, not a boxed chip.

**Course header (below top bar):**

```
● CS 101 — Intro to Computer Science     [Study Room · live] [+ Invite]
  Spring 2026 ▾ · Dr. Alex Johnson
Overview | Chat | Questions | Resources | Office Hours | People
```

- Subtitle **synced** with breadcrumb cohort — **one picker state**, two display locations.
- Cohort in subtitle: `Spring 2026 ▾` as **text + chevron**, not a rounded button.
- Learner with one cohort: static subtitle (`Spring cohort · Dr. Alex Johnson`), no `▾`.

### 4.2 Tabs (all `journey-4*`)

`Overview · Chat · Questions · Resources · Office Hours · People`

Every tab mockup **must** include full course header + tab strip above content.

### 4.3 Per-tab content (learner)

| Tab | Learner | Instructor / owner extras |
|-----|---------|----------------------------|
| **Overview** | Progress, this week, recent activity, up next | + weekly **Questions digest** (channel summary content) |
| **Chat** | Two-tone channel + chat panels | **`+` on CHANNELS / VOICE headers** to add channel (not bottom dashed button) |
| **Questions** | Two-tone list + AI thread with **Sources** | FAQ candidates, TA queue pick-up, reply |
| **Resources** | Week folders, download only, **no Upload** | **Upload**, organize folders, **AI Study Assistant** install modal |
| **Office Hours** | Open voice: listen, raise hand, leave | **Schedule / Start** session |
| **People** | Roster, Message classmates | **+ Enroll**, invite link, TA role + routing (see §4.7) |

### 4.7 People — instructor / owner (`4f` + O7)

**Layout:** Start from learner **4f** sectioned roster — **full width, no right panel** (no duplicate invite card).

**Course header:** title + subtitle + tab strip only — **do not** put enroll/invite actions in the course header (unlike learner `+ Invite` on 4f).

**People toolbar (first row below tabs, inside tab content):**

| Left | Right |
|------|-------|
| `Search people…` + chips **All · Enrolled · Pending** | outline **Copy invite link** + primary **`+ Enroll learner`** |

All on **one horizontal row** — enroll/invite align with search/filters, not the course title bar.

- **`+ Enroll learner`** → modal (email/name; optional assign TA on enroll). **No** separate “manual enroll” link elsewhere.
- One-line cohort summary on **second row**: `24 learners · 2 TAs · 1 pending`.

**Bulk assign (O7c):** same table as O7a. When ≥1 learner row is checked, **replace the table header row** (do not add a separate bulk bar):

| Default header (O7a/O7b) | Selected header (O7c) |
|--------------------------|------------------------|
| `☐` · **Name** · **Status** · **Assigned TA** · `⋯` | `☑` · **`3 selected`** · **`Assign TA ▾`** · **`Clear selection`** |

- **`Assign TA ▾`** → popover: Maria Gonzalez, Jordan Kim, Unassigned.
- **`Clear selection`** → uncheck all; header reverts to column names.
- Section title **LEARNERS (24)** stays above the table in both states.
- Header checkbox = select all / deselect all on current filter.

**TA rows:** same row pattern as learners — avatar, name, **TA** badge, **`⋯`** (Message, Remove TA role, etc.).

**Sections (same as learner 4f):**

```
INSTRUCTOR
  Dr. Alex Johnson · OWNER

TEACHING ASSISTANTS                              [+ Add TA]
  Maria Gonzalez · TA
  Jordan Kim · TA

LEARNERS (24)
  ☐ | Name | Status | Assigned TA | ⋯
  ☐ Sam Chen      Enrolled   Maria ▾   ⋯
  ...
```

**Two TA concepts (do not conflate):**

| Action | Meaning | UI |
|--------|---------|-----|
| **Add TA** | Grant **TA role** for this cohort | **`+ Add TA`** on **TEACHING ASSISTANTS** header → **popover combobox** (search hub/course members) — O7b |
| **Assigned TA** | Route learner’s questions to a TA | Per-row dropdown: cohort TAs + **Unassigned** — inline only, no modal. Hide column if only one TA. |

**Add TA popover (O7b):** anchored under **`+ Add TA`**; search field `Search members…`; results with avatar + name + role hint; pick → person appears in TA section.

**Row ⋯ menu:** Learners — Message, Remove from cohort, etc. TAs — Message, Remove TA role, etc.

**Approved mockups:** O7a [`owner-o7a-people-roster-default.png`](mockups/owner-flow/owner-o7a-people-roster-default.png) · O7b [`owner-o7b-people-add-ta-popover.png`](mockups/owner-flow/owner-o7b-people-add-ta-popover.png) · O7c [`owner-o7c-people-bulk-assign-ta.png`](mockups/owner-flow/owner-o7c-people-bulk-assign-ta.png).

### 4.4 Chat tab layout (`4b`, `5b` lounge)

Two bordered panels side by side (three-tone: outside darkest → channel panel → chat panel lightest):

- Messages: natural spacing, **no horizontal dividers** between messages.
- Composer: bottom of **chat panel only**.
- **Add channel:** `+` icon on **CHANNELS** and **VOICE** section headers — **instructor + owner** only; learners see read-only list. **No** dashed “Add channel” at panel bottom.

### 4.5 Resources (`4d`)

- Learner: search, filter chips, week folders, AI-approved badges, download — **full width, no right panel**.
- Instructor / owner — same layout **plus** a resources toolbar row (below tabs):
  - **Left:** `Search resources…` + filter chips (All / Slides / Recordings / Assignments)
  - **Right:** **`Upload`** (primary solid) + **`Install AI Study Assistant`** (secondary outline, sparkle icon) — visible **before** modal opens; clicking opens the install modal
  - After installed: replace with **`AI Study Assistant · Active`** (outline) + gear/settings; no duplicate card in right panel

### 4.6 Office Hours (`4e`)

**Open voice hours** (not Zoom grid, not numbered admit queue):

- Join to **listen** (mic off); **Raise hand** to speak; host calls on raised hands.
- Single bordered card; Speaking now / Listening / Hands raised / control bar.

**Instructor / owner — scheduling (cohort-scoped):**

Use a **list + form** on the Office Hours tab — **not** a full calendar widget here. The global **Calendar** nav aggregates office hours, hub events, and deadlines as a **read-only cross-course view** (`journey-8`); tap an item → deep-link to course **Office Hours** tab (instructors) or event/course surface (learners).

**Layout when hosting + managing schedule:**

| Area | Content |
|------|---------|
| **Left (main)** | **Live:** open-voice session card (host controls, End session, raised hands) — see O6a. **Not live:** **Next office hours** summary card — date/time, recurring badge, “Opens for learners at …”, optional last-session line (“May 9 — 14 joined, 45 min”), secondary **Start session early** — see O6b. |
| **Right panel** | **Upcoming sessions** list (O6a) or **schedule/edit form** (O6b) |

**Right panel — default (list view):**

- Header: **Upcoming office hours** + **`+ Schedule`** (outline; only action that opens the form)
- Rows: date · time · duration · recurring badge (if weekly)
- Per row actions: **Edit** · **Cancel session** (confirm: “Learners won’t be able to join this window”)
- Empty state: “No sessions scheduled” + **`+ Schedule`**

**Right panel — create / edit (form view):**

- Replaces list or slides over it when **`+ Schedule`** or **Edit** is clicked
- Fields: date, start time, duration, **Repeat weekly** toggle
- Footer: **Cancel** · **Save** (create/update)
- **Edit only:** destructive **Delete session** link at bottom
- **No** separate `Schedule session` button when this form is open; **Save** is the primary CTA

**Remove / cancel:** **Cancel session** on list row (upcoming only). Cannot delete a session that is **live** — use **End session** first.

**Learner sync:** Saved sessions appear on learner `4e` when window is open; **Home / Up next** and **Calendar** show the next occurrence.

**Approved mockups:** O6a [`owner-o6-office-hours-schedule-host.png`](mockups/owner-flow/owner-o6-office-hours-schedule-host.png) (list view — live left) · O6b [`owner-o6-office-hours-schedule-edit.png`](mockups/owner-flow/owner-o6-office-hours-schedule-edit.png) (form view — next-session summary left, not live).

### 4.8 Data scope

| Surface | Scope |
|---------|--------|
| Resources (default) | **Course** |
| Chat, Questions, People, Office Hours, TA queue | **Cohort** |
| Discover courses cards | **Course** (pick cohort on Join) |

---

## 5. Study Server community (`journey-5*`)

Open from server group / hub banner. Tabs on **every** `5*` screen:

`Announcements · Lounge · Events · Discover courses · Members`

| Tab | Layout | Right panel |
|-----|--------|-------------|
| **Announcements** (`5a`) | Announcement feed cards | **About** + **Upcoming events** ✓ |
| **Lounge** (`5b`) | Two-tone chat like `4b` | **None** |
| **Events** (`5c`) | Full-width event cards; Upcoming / Past / **Going** | **None** |
| **Events detail** (`5c-detail`) | Center **modal** over dimmed list | — |

**Owner / instructor on Events tab:**

- **`+ Create event`** in tab toolbar (right).
- Each event card: learner sees **Going** / **Interested**; owner/instructor also sees **Edit** link (opens same modal as Create, prefilled).
- **Edit** only when user can manage that event (hub owner/admin, or course instructor for course-linked events).

**Approved mockups:** O10a events list with **Edit** · O10b Create event modal.
| **Discover courses** (`5e`) | **2×2 course card grid**; filters: All / Enrolled / Open / Opening soon | **None** |
| **Members** (`5d`) | Staff + Learners roster | **None** |

**Hub header course count** must match sidebar course list (e.g. “3 courses”).

### 5.1 Discover courses tab — role-gated actions

Every course card in Discover **already has an instructor** (assigned when the course was created). Learners **join** courses; they do **not** apply to teach a specific existing course.

| Role | Actions on Discover tab |
|------|-------------------------|
| **Learner** | Browse, **Join**, invite code |
| **Learner (not yet instructor)** | **Apply to become an instructor** — separate hub-level control (see below); **not** on course cards |
| **Instructor / server owner** | **+ Create course** (toolbar on Discover tab) |

**Apply to become an instructor (MVP):**

- **Where:** Discover courses tab — **separate button** in the tab toolbar or hub header area (e.g. outline **“Apply to become an instructor”**). **Not** on individual course cards.
- **Why:** A course created by an owner/instructor already has an instructor. Learners who want to teach must **earn instructor role first**, then use **+ Create course** to publish their own course.
- **Flow:** Learner submits application (short form) → owner reviews in **Members** or **Settings** → on approval, user gets instructor role → **Teaching** nav appears → **+ Create course** unlocks.

Courses listed are **this study server only** — not a global marketplace route.

---

## 6. Onboarding and creation flows

### 6.1 Create study server

- Trigger: **+ Join or create** → **Create study server**.
- **Modal wizard** (dimmed shell behind) — same steps as a full-page wizard would have; **not** a separate product/site for MVP.
- **Step 0 — server type:** School / organization · Program / bootcamp · Personal / small group (personal de-emphasized but included).
- Steps: basics (name, description, icon) → invite team → review.
- Copy targets **organizations and schools**, not gaming servers.

### 6.2 Create course

- Trigger: hub **Discover courses** → **+ Create course** (owner + approved instructor).
- Flow: create **course** → create **first cohort** (required) → publish to Discover grid.
- Later: **+ Add cohort** on same course (Spring / Fall).

### 6.3 Become an instructor

| Path | Mechanism |
|------|-----------|
| Create server | User becomes **study server owner** (can create courses immediately) |
| Invite | Owner/admin invites member as instructor |
| **Apply to become an instructor** | Learner uses **hub-level** button on Discover courses (or Members) — **not** per-course card; owner approves → instructor role |
| Create course | **After** instructor role: **+ Create course** on Discover tab → assign self as instructor |

Sign-up default = **learner**. Do not ask instructor intent on every registration.

**Do not:** put “Apply to teach” on a course card for an existing course that already has an instructor.

### 6.4 Cohort enrollment

- **Not** a separate LMS page — lives in **course → People** tab (instructor).
- Invite link copy, manual enroll, roster table, TA assignment per learner.
- Hub **Members** (`5d`) = server community roster; **course People** (`4f`) = cohort enrollment.

---

## 7. Roles and permissions (UI visibility)

| Action | Owner | Admin | Instructor | TA | Learner |
|--------|-------|-------|------------|-----|---------|
| Create study server | ✓ | — | — | — | — |
| Settings / billing | ✓ | maybe | — | — | — |
| Create course | ✓ | ✓ | ✓ | — | — |
| Apply to become instructor | — | — | — | — | ✓ (hub-level request) |
| Teaching nav | ✓ | ✓ | ✓ | — | — |
| Upload resources | ✓ | ✓ | ✓ | — | — |
| AI install (HITL) | ✓ | ✓ | ✓ | — | — |
| Add chat channel | ✓ | ✓ | ✓ | — | — |
| Schedule / host office hours | ✓ | ✓ | ✓ | — | — |
| FAQ approval | ✓ | ✓ | ✓ | — | — |
| TA queue | ✓ | ✓ | ✓ | ✓ | — |
| Enroll learners | ✓ | ✓ | ✓ | — | — |

---

## 8. Where legacy owner screens map (v2)

| Legacy `owner-flow/` concept | v2 home |
|------------------------------|---------|
| `create-study-server.png` | Modal wizard from + Join or create |
| `cohort-enrollment.png` | Course → **People** tab |
| `ai-assistant-install.png` | Course → **Resources** → AI modal |
| `instructor-dashboard.png` | **Teaching** sidebar nav |
| `ta-queue.png` | Course → **Questions** (TA + instructor) |
| `faq-approval.png` | Course → **Questions** (FAQ candidates section) |
| `channel-summary.png` | Course → **Overview** (instructor digest block) |
| `saas-billing.png` | **Settings** modal → Plan & Billing |
| `office-hours-voice.png` (old queue grid) | Replaced by learner `4e` open-voice model; instructor schedules on **Office Hours** tab |

---

## 9. Learner journey index (approved mockups)

| ID | File |
|----|------|
| 1 | `journey-1-signup-invite.png` |
| 2 | `journey-2-welcome-joined.png` |
| 3 | `journey-3-home.png` |
| 4a–4f | course workspace tabs |
| 5a–5e | community tabs (+ `5c-detail`, + `5e-apply`) |
| 6 | `journey-6-inbox.png` |
| 7 | `journey-7-friends-dm.png` |
| 8 | `journey-8-calendar.png` |

**Dropped:** `journey-8-search-overlay` (⌘K global), `journey-9-discover-courses` (global discover → replaced by `5e`).

---

## 10. Owner journey index (to regenerate on v2 chrome)

| ID | Screen |
|----|--------|
| O1 | Create study server modal wizard (full flow) |
| O2 | Create course + cohort (modal from **+ Create course** on Discover tab) |
| *(learner 5e-apply)* | Apply to become an instructor — [`mockups/learner-flow/journey-5e-community-discover-apply-instructor.png`](mockups/learner-flow/journey-5e-community-discover-apply-instructor.png) |
| O4 | Resources — instructor upload + AI install modal |
| O5 | Questions — FAQ candidates + TA queue |
| O6a | Office Hours — live host + upcoming list (`+ Schedule`, Edit, Cancel session) |
| O6b | Office Hours — next-session summary (left) + schedule/edit form (Save, Delete session) |
| O7a | People — default roster (full-width sections) — [`owner-o7a-people-roster-default.png`](mockups/owner-flow/owner-o7a-people-roster-default.png) |
| O7b | People — **+ Add TA** popover combobox — [`owner-o7b-people-add-ta-popover.png`](mockups/owner-flow/owner-o7b-people-add-ta-popover.png) |
| O7c | People — bulk **Assign TA** (selected rows) — [`owner-o7c-people-bulk-assign-ta.png`](mockups/owner-flow/owner-o7c-people-bulk-assign-ta.png) |
| O8 | Teaching dashboard — [`owner-o8-teaching-dashboard.png`](mockups/owner-flow/owner-o8-teaching-dashboard.png) |
| O9 | Settings modal — Plan & Billing — [`owner-o9-settings-plan-billing.png`](mockups/owner-flow/owner-o9-settings-plan-billing.png) |
| O10a | Create community event — events list with **Edit** — [`owner-o10a-community-events-owner.png`](mockups/owner-flow/owner-o10a-community-events-owner.png) |
| O10b | Create community event — modal — [`owner-o10b-create-community-event.png`](mockups/owner-flow/owner-o10b-create-community-event.png) |

---

## 11. Component specs

Annotated sheets in [`specs/`](specs/): sidebar, topbar-tabs, chat-components, cards-context-panel.

Update `chat-components-spec` when regenerating: **`+` on section headers**, not dashed bottom add.

---

## 12. Migration plan (docs + assets)

1. **Done:** Decisions in this file + `layout-rules.md`; all PNGs in [`mockups/learner-flow/`](mockups/learner-flow/) and [`mockups/owner-flow/`](mockups/owner-flow/).
2. **Next:** Implementation from mockups in Production Frontend (#48–#59).

---

## 13. Deferred / not mocked

- **Global cross-server search** (post-MVP; MVP uses context-scoped ⌘F only).
- **Breakout rooms** for office hours.

---

## Related docs

- [`specs/layout-rules.md`](specs/layout-rules.md) — mockup generation rules
- [`specs/home-screen-spec.md`](specs/home-screen-spec.md) — `/home` content
- [`../visibility-and-social-model.md`](../visibility-and-social-model.md) — enrollment + friends
- [`../../CONTEXT.md`](../../CONTEXT.md) — domain glossary
- [`mockups/owner-flow/README.md`](mockups/owner-flow/README.md) — owner screen index
- [`mockups/learner-flow/README.md`](mockups/learner-flow/README.md) — learner journey index
