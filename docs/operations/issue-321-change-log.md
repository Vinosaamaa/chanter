# Issue #321 change log

Repository: `Vinosaamaa/chanter`. Parent: #248. Branch: `codex/321-ai-answer-ui`, from main db480a3. One frontend worktree and one final PR. The #254 worktree/preview is preserved.

Acceptance: authorized model/type controls; truthful capability and billing copy; saved-answer attribution; status/error/EOF recovery; account/channel/request ownership; meaningful regression, real-service and visual evidence. The design and system boundary are recorded in `docs/product-design/assistant-answer-controls.md` before implementation. No native companion or subscription integration is included.

Implementation and verification are pending.

## Implementation and local verification

- Added authenticated catalog types/fetching and explicit model/mode query parameters.
- Added source/quotation controls, billing/capability explanations, unavailable mode handling and saved audit attribution.
- Reject incomplete streams and structured errors; release readers and guard every stream callback/settlement by active request ownership.
- Explicit source-only recovery after an uncertain provider attempt; no automatic provider retry.
- Gate invisible staff tool requests by existing capabilities and make descriptive citation chips noninteractive.
- Red: four stream contract assertions, three request/recovery ownership assertions, one learner audit assertion failed against prior behavior. New control acceptance tests were authored before the component existed.
- Green: 274 frontend tests across 77 files; lint; production build; fixture exclusion; unchanged bundle budgets. CSS initially exceeded its cap by 773 bytes, then passed after shared-button reuse (0.90 kB new route CSS).
- Added hosted synthetic viewport/keyboard/error/recovery browser tests and a separate actual seeded source-only/persisted-reload journey. Hosted results and screenshot critique remain pending.

Design: `docs/product-design/assistant-answer-controls.md`. System review: `docs/operations/issue-321-system-review.md`. Rich record: `architecture-review-ai-answer-controls@1`.
