# Chanter learning desk, version 3

Issue: [#254](https://github.com/Vinosaamaa/chanter/issues/254). Status: implementation direction, internal visual review pending.

This document supersedes the visual rules and screenshots in `DESIGN-DECISIONS.md` and `specs/layout-rules.md`. Their enrollment, role, search-scope, Course/Cohort, and social boundaries remain in force. The owner requested a full reconstruction after rejecting the old interface.

## The job

Chanter brings a Course, its conversations, its learning materials, and its people together. A returning learner needs to resume a Course, see a timely reply or scheduled Office Hours, and get to the relevant conversation without deciphering a dashboard. An instructor needs the same learning environment plus a clear work queue. Mobile is a primary reading and messaging surface.

The design borrows its structure from an open course notebook: a quiet navigation index, a generous working page, and a small schedule in the margin. The distinctive element is the Course cover: a strong typographic title with a narrow colored binding. Covers represent actual Courses, never invented activity or completion. We spend visual emphasis there; all other components stay calm.

## Compact system

| Token | Value | Role |
|---|---|---|
| Paper | `#FFFFFF` | Reading, forms, messages |
| Desk | `#F3F6FA` | Page surroundings, secondary areas |
| Ink | `#192C46` | Body, headings, navigation |
| Quiet ink | `#596A80` | Supporting text with readable contrast |
| Chanter blue | `#2458D3` | Primary action, selected location, links |
| Rule | `#DCE3EC` | Actual grouping, input boundaries |

Success uses `#197451`; errors use `#B42332`; warning uses `#8A5C12`, always with text or an icon. Course bindings use a small stable categorical palette and never encode status alone. No decorative gradients, artificial shadows on every block, or tinted dark text masquerading as a theme.

Type: self-hosted Instrument Sans for interface and display, with system sans fallback. Its open forms and confident, compact headings suit long Course names and dense conversations. Body 15/24, controls 14/20 (16 on narrow inputs), supporting text 13/20, section headings 20/28, page heading 32/38, marketing display 56/60 scaling down to 38/42. Headings use weight 600 and tight but readable tracking. Sentence case throughout. Paragraphs max 72ch; conversation text max 76ch.

Space follows 4, 8, 12, 16, 24, 32, 48, 64px. Corners express hierarchy: 6px controls, 10px surfaces, 16px dialogs; circles only for real people and status dots. Shadows only on floating menus/dialogs. Focus uses a 3px blue outline with a white separation. Controls have at least 44px touch targets; dense desktop rows may use 36px height with sufficient spacing.

## Layout decisions

Everything is left aligned except compact empty-state copy and marketing's short opening. Sidebar width stays 248px instead of growing proportionally with the display. Standard working content stops at 1440px; reading text stops sooner. On a 4K display, the content stays readable instead of stretching into long empty strips. Chat and data views can use more width within bounded columns.

Desktop Home:

```text
┌──────────────────┬──────────────────────────────────────────────────────┐
│ Chanter          │ Home                         Search courses   Inbox │
│                  ├──────────────────────────────────────────────────────┤
│ Home             │ Friday, September 12                                 │
│ Teaching*        │ Good morning, [name]                                 │
│ Inbox            │ Your Courses and conversations, in one place.       │
│ Calendar         │                                                      │
│ Friends          │ Continue learning                Up next             │
│                  │ ┌─Course cover────────────────┐  Timed agenda        │
│ Study Servers    │ │ Title                       │  with real links     │
│ [real groups]    │ │ Cohort / instructor         │                      │
│  [real courses]  │ │ Actual progress if known    │  View calendar       │
│                  │ └─────────────────────────────┘                      │
│ Join or create   │ More course covers                                   │
│ Account          │ Needs attention: actual replies / events / updates  │
└──────────────────┴──────────────────────────────────────────────────────┘
```

Tablet, 768px: 72px navigation rail for primary destinations, Course index in an accessible drawer, two bounded content columns only when readable; Home schedule follows Courses when needed. No scaled-down desktop sidebar.

Mobile, 360–390px:

```text
┌──────────────────────────────┐
│ Chanter / context   Search   │
├──────────────────────────────┤
│ Date                         │
│ Good morning, [name]         │
│ Continue learning            │
│ ┌─Course───────────────────┐ │
│ │ Full title wraps         │ │
│ │ Cohort, instructor       │ │
│ └──────────────────────────┘ │
│ Next Course                  │
│ Up next                      │
│ Actual agenda                │
├──────────────────────────────┤
│ Home  Inbox  Friends  Browse │
└──────────────────────────────┘
```

Mobile bottom navigation offers the three frequent personal destinations plus Browse, which opens the full Course/Study Server/account drawer. Calendar and Teaching remain one drawer action away. Course tabs scroll horizontally with a visible edge affordance. Chat becomes a single conversation with an explicit channel chooser; the composer stays above safe-area and keyboard boundaries. Long names wrap; no title is lost behind an ellipsis where it is the primary content.

## Surface families

| Surface | Structure and interaction |
|---|---|
| Home | Real Course covers and a compact schedule; actionable updates form a list, not equal statistic cards. Loading skeletons reflect the final layout; failed summary has a retry. |
| Course | Full readable title, Cohort context, horizontal tabs, then one coherent working surface. Overview highlights next actions; Resources is a material list; Questions is a question queue and reading pane. |
| Chat and Direct Messages | Quiet message canvas, clear sender/time grouping, one anchored composer. Navigation and conversation split on wide screens, one pane at a time on phones. |
| Community | Distinct Study Server heading and tab set. Announcements read like posts; Course discovery uses the same cover grammar; members are people rows. |
| Inbox | Filter and unread controls above a scannable list; no decorative dashboard summaries. Read state is both visual and semantic. |
| Calendar | Month grid on larger screens with a clear selected-day agenda; phone agenda is prioritized while day navigation stays usable. |
| Teaching | A prioritized operational list, with real counts and links into the owning Course tools. Role-gated actions stay in the same shell. |
| Settings, billing, administration | Narrow labeled forms and readable usage/permission tables. Irreversible actions are grouped separately; unavailable capabilities explain the reason. Never invent account settings or working billing. |
| Marketing | Lead with the actual learning loop: a Course, a question, a person who can help. Show a clearly illustrative product composition, no fabricated metrics/testimonials. Use the same Course cover grammar and direct registration/sign-in actions. |
| Auth and legal | Compact single-purpose form, persistent labels, adjacent error/help, generous reading measure. Preserve verification, cookie sessions, retry and sign-out guarantees from #242. |

## States and accessibility

- Loading names what is loading and avoids falsely displaying zero counts. Empty states name a useful next action only when a real route exists.
- Errors distinguish recoverable fetch failures from forbidden/unavailable capability; retry is real. No silent placeholder controls.
- Navigation includes a skip link, current-page state, named landmarks, predictable focus return and Escape dismissal. Hidden drawers cannot receive focus.
- Drawers/dialogs contain keyboard focus while open; closing restores the invoking control. Browser zoom, 200% text sizing, high contrast and reduced motion remain usable.
- Use semantic headings, lists, tables, labels and live status. Icons supplement text; accessible names match visible actions.
- Reduced motion removes transitions, rather than merely shortening them. Safe-area padding and dynamic viewport height protect mobile controls.
- Device-session copy states that revoked devices stop renewing access; existing access can last up to 15 minutes.

## Pre-build self-review

The first idea was a white dashboard with a grid of colorful Course cards and a dark navigation panel. That could fit almost any SaaS product. Revised: a light index rather than another dark dashboard, typographic Course covers rather than identical cards, no summary statistics above learning, and mobile conversation/navigation patterns planned first. Blue is a navigation/action signal, not a wash behind every section. The identity comes from the Course binding and deliberate type, not decoration.

Risk: removing old surface boundaries could flatten hierarchy. Keep boundaries only around objects that can be opened or edited, and separate informational sections with space and clear headings. Risk: light colors can hide state. Use text, weight, focus rings and accessible contrast, then inspect screenshots and axe results. Risk: redesigning every page can accidentally change authorization. Keep API hooks and capability gates intact; test behavior when structural changes touch navigation, state or controls.

## Evidence plan

First internal review: rendered Home at 1280 and 390px. Then route-family review at 360/390, 768, 1280, 1920 and 3840px, portrait/landscape, keyboard-only and reduced motion. Record fixture-based visual evidence separately from real authenticated full-stack evidence. #254 remains open until its backend-dependent functionality gates, hosted checks, review and release requirements are satisfied.


## Browser review and performance decisions

The first broad hosted review rendered the real application with synthetic API content. Review found a visibility-transition focus failure, hidden portrait/landscape chat composers, an overlapping question avatar, a stacked phone question queue, and unconfigured OAuth setup text. These are concrete browser findings, with fixes and retained assertions. Public landing direction is approved for extension; complete responsive and accessibility acceptance remains open.

Route imports are deferred by the router. The initial JavaScript asset fell from roughly 1.23 MB raw to 324 KB raw; the voice client remains a deferred chunk. Separate gzip streams increase the sum of compressed assets from about 332 KB to 375 KB without duplicating application modules. The agreed budgets retain the 1.3 MB total raw cap, allow 400 KB total gzip, cap initial entry plus static imports at 120 KB gzip, and cap any deferred chunk at 130 KB gzip. The manifest check deduplicates shared imports and excludes dynamic imports until a route is requested. It also measures landing, sign-in and signed-in Home including their layout dependencies, with caps of 140, 140 and 150 KB gzip respectively. Observed transfers before secure-session integration: about 109, 119 and 134 KiB gzip. CSS remains capped at 220 KB raw / 45 KB gzip.

Viewport tests at 640 by 450 CSS pixels and 2x device density provide a 200%-zoom-equivalent reflow check for a 1280 by 900 display. They are not manual browser-chrome zoom or screen-reader testing. Manual assistive-technology checks and p75 Core Web Vitals on a production-like connection remain required before final release acceptance.
