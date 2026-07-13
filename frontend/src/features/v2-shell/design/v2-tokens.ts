/**
 * UI v2 layout reference — visual source of truth is Chativity MockUp UI
 * (`/Users/wenkxu/Projects/MockUp UI/app/globals.css` + `page.tsx`).
 * Styles live in `v2-mockup.css` (scoped copy of globals.css).
 */

export const V2_SOURCE = {
  mockupProject: 'MockUp UI (Chativity)',
  mockupCss: 'app/globals.css',
  mockupPage: 'app/page.tsx',
  chanterCss: 'frontend/src/features/v2-shell/design/v2-mockup.css',
} as const

/** Design canvas used for screenshot QA (reference mockup viewport). */
export const V2_LAYOUT = {
  designViewportWidth: 1280,
  sidebarWidthCss: 'clamp(290px, 22.27vw, 420px)',
  topBarHeightCss: 'clamp(70px, 6.3vw, 81px)',
  upNextWidthCss: 'minmax(280px, 314px)',
  courseGridCols: 2,
  attentionGridCols: 3,
} as const
