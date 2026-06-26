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

Resolved the single follow-up comment from the second CodeRabbit review.

| Comment | Resolution |
|---------|------------|
| **Trivial** — `use-faq-approval-panel.ts`: impure `setCandidates` updater | Compute `next` outside updater; set candidates, index, and draft separately; add `candidates` to deps |

## Pass 3

Resolved the stale-candidates race on FAQ approve.

| Comment | Resolution |
|---------|------------|
| **Major** — post-await candidate removal used stale closure | Remove approved group keyed by `sourceSupportQuestionIds`; block refresh/selection while `isSaving` |

## Pass 4

Resolved outside-diff and duplicate comments from the fourth review.

| Comment | Resolution |
|---------|------------|
| **Major** — FAQ editor partially unlocked during save | Disable Edit, Cancel, and textareas while `isSaving`; guard `startEditApproved` / `clearEdit` in hook |
| **Major** — `nextCandidates` read from `setCandidates` side effect | Compute filtered list before `setCandidates` (safe because save locks concurrent candidate edits) |

## Pass 5

Resolved reload-during-save race on FAQ approve.

| Comment | Resolution |
|---------|------------|
| **Major** — approve allowed while `isLoading` | Guard `approveOrUpdate` when `isLoading`; disable Approve button during reload |

## Pass 6

Resolved re-entrant save guard.

| Comment | Resolution |
|---------|------------|
| **Major** — duplicate `approveOrUpdate` while save pending | Short-circuit when `isSaving`; add to callback deps |

## Pass 7 (CLI + follow-up)

| Comment | Resolution |
|---------|------------|
| **Trivial** — FAQ textareas editable during reload | Disable Question/Answer fields while `isLoading` |
| **Minor** — Support label inside `<ul>` | Move Support heading outside list in `ChannelSidebarColumn` |
| **Minor** — `loadWaitlist` for joiners after join | Deferred: join-only users get 403 on waitlist API (see Pass 1) |
| **Minor** — approved FAQ fetch swallows errors | Deferred: non-blocking secondary load; empty list is acceptable fallback |
