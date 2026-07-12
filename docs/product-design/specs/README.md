# Component specs — uniform across all screens

Annotated reference sheets for the course-first redesign. **Every regenerated mockup must follow these specs** so screens look like one product. Use them as reference images when generating new mockups.

## Spec sheets

| File | Covers |
|------|--------|
| [`sidebar-spec.png`](sidebar-spec.png) | Left sidebar: Home/Inbox/Calendar/Friends nav, server-grouped course list, active states (filled pill for nav, accent bar + tint for course), unread badges, dashed "+ Join or create", profile row **flush to bottom edge — no gap below avatar** |
| [`topbar-tabs-spec.png`](topbar-tabs-spec.png) | Top bar (breadcrumb, centered search with **⌘F** hint and optional **muted scope preview** in course workspace e.g. `in:#general`, notifications — **no avatar; profile lives in the sidebar**), course workspace tab strip (Overview · Chat · Questions · Resources · Office Hours · People, indigo underline active), community tab strip (Announcements · Lounge · Events · Discover courses · Members), button set (primary solid / secondary outline / tertiary dashed), unread badges, role chips (OWNER/ADMIN/TA) |
| [`chat-components-spec.png`](chat-components-spec.png) | Inner channel list (active tint, unread at row end, voice room with live count, **`+` on CHANNELS/VOICE section headers** for instructor/owner — **no** dashed add at panel bottom), message rows (36px avatar, name + role chip + timestamp, reaction pills, grouped messages), composer (attach left, emoji + indigo send right, 44px rounded input) |
| [`cards-context-panel-spec.png`](cards-context-panel-spec.png) | Context panel cards (12px radius, 1px border, icon metadata rows, date chips), course card (color dot, accent progress bar, activity footer), announcement card (author row, title, engagement footer), empty state pattern (icon + line + CTA) |

## Shared tokens

- **Background:** dark navy; **accent:** indigo/blue
- **Course colors:** each course gets one accent (blue CS 101, green MATH 201, yellow BIO 150, purple ECON 210, pink ENG 120) used for its dot, progress bar, and selected-state tint
- **Role chips:** purple OWNER, blue ADMIN, green TA — uppercase, small
- **Radius:** 12px cards, pill nav items, 44px rounded composer

## Home screen contents (canonical)

Full spec for issues and markdown updates: **[`home-screen-spec.md`](home-screen-spec.md)**.

Navigation, chat layout, Resources learner rules, and journey file naming: **[`layout-rules.md`](layout-rules.md)**.

**Canonical decisions:** **[`../DESIGN-DECISIONS.md`](../DESIGN-DECISIONS.md)**.

## Not spec'd (describe in generation prompts instead)

Modals/dialogs, form fields and validation, loading states.
