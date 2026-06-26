# Issue #54 — CodeRabbit fix log

## Pass 1

Resolved all inline comments from the first CodeRabbit review on PR #71.

| Comment | Resolution |
|---------|------------|
| **Major** — `use-office-hours-panel.ts`: learners with `canJoinOfficeHours` only hit 403 on waitlist fetch | Guard `listOfficeHoursWaitlist` behind `canManageOfficeHours` on initial load; `loadWaitlist` no-ops unless `canManage` |
| **Minor** — `use-faq-approval-panel.ts`: stale `questionDraft` after approve | Reset `questionDraft` to next candidate's `representativeQuestion` when removing approved candidate |
| **A11y** — `FaqApprovalPanel.tsx` labels | Added `htmlFor` / `id` on Question and Answer fields |
| **A11y** — error/success banners | Added `role="status"` and `aria-live="polite"` on TaQueue, OfficeHours, and FAQ panels |
| **A11y** — `SupportOperationPage.tsx` tabs | `aria-current="page"` on active tab link |
| **UX** — course vs cohort missing | Split "course not found" and "no cohort yet" messages |
| **DRY** — support operation list | Exported `SUPPORT_OPERATIONS` from `shell-routes.ts`; reused in sidebar and page |

## Pass 2

_Pending re-review._
