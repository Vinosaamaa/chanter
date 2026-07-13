# UI v2 design tokens

**Visual source of truth:** Chativity **MockUp UI** project (`MockUp UI/app/globals.css` + `page.tsx`) — pixel-matched to `journey-3-home.png` with responsive scaling.

**Chanter implementation:** `frontend/src/features/v2-shell/design/v2-mockup.css` (scoped copy of `globals.css` under `.v2-app-shell`).

Agents implementing UI v2 **must** use the mockup CSS classes and structure — do not invent parallel token systems or one-off Tailwind in feature code.

## Reference files

| MockUp UI | Chanter |
|-----------|---------|
| `app/globals.css` | `design/v2-mockup.css` |
| `app/page.tsx` | `components/V2Sidebar.tsx`, `V2TopBar.tsx`, `HomeSections.tsx`, `pages/HomePage.tsx` |
| `lucide-react` icons | same package in `frontend` |

## Layout (fluid / responsive)

The mockup uses **viewport-relative sizing**, not fixed px tokens:

| Element | CSS |
|---------|-----|
| Sidebar | `clamp(290px, 22.27vw, 420px)` |
| Top bar | `min-height: 81px` (shrinks on short viewports) |
| Up next column | `minmax(280px, 314px)` in lower grid |
| Greeting | `clamp(32px, 2.68vw, 48px)` |
| Course grid | 2 columns → 1 on narrow screens |

Breakpoints: `1320px`, `1180px` (mobile drawer), `860px`, `720px`, `440px`, `2200px`.

## Color (CSS variables on `.v2-app-shell`)

| Token | Value | Use |
|-------|-------|-----|
| `--ink` | `#f7f7fa` | Primary text |
| `--muted` | `#b7b5bf` | Secondary text |
| `--line` | `rgba(164,169,179,0.32)` | Card borders |
| `--navy` | `#001429` | Surfaces |
| `--navy-deep` | `#001124` | Shell background |

Course accents use gradient pairs from `course-accent.ts` (mockup palette).

## Typography

Fluid `clamp()` / vw sizes from mockup — e.g. nav `20px`, section titles `24–26px`, greeting up to `48px` on large screens. Font: **Inter**.

## Components (CSS classes)

Use mockup class names directly:

| Class | Use |
|-------|-----|
| `.app-shell` | Full-height flex row |
| `.sidebar`, `.main-nav`, `.community` | Left nav |
| `.topbar`, `.search-box` | Top bar (⌘K) |
| `.notice-row`, `.notice` | Attention cards |
| `.course-card`, `.course-meta` | Continue learning |
| `.up-next`, `.timeline` | Right column |

## Component anatomy

Same as mockup `page.tsx` — attention cards with `<strong>` + `<span>` suffix; course cards with `--course-color` CSS variables; timeline with gradient icon boxes.
