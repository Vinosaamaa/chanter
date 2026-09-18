# Issue #321 change log

Repository: `Vinosaamaa/chanter`. Parent: #248. Branch: `codex/321-ai-answer-ui`, from main db480a3. One frontend worktree and one final PR. The #254 worktree/preview is preserved.

Acceptance: authorized model/type controls; truthful capability and billing copy; saved-answer attribution; status/error/EOF recovery; account/channel/request ownership; meaningful regression, real-service and visual evidence. The design and system boundary are recorded in `docs/product-design/assistant-answer-controls.md` before implementation. No native companion or subscription integration is included.

Implementation is complete in PR #322. The resumed branch includes merged backend repair #319. Final review and exact-head checks govern integration; the issue remains open through required merged-main and release evidence.

## Implementation and local verification

- Added authenticated catalog types/fetching and explicit model/mode query parameters.
- Added source/quotation controls, billing/capability explanations, unavailable mode handling and saved audit attribution.
- Reject incomplete streams and structured errors; release readers and guard every stream callback/settlement by active request ownership.
- Explicit source-only recovery after an uncertain provider attempt; no automatic provider retry.
- Gate invisible staff tool requests by existing capabilities and make descriptive citation chips noninteractive.
- Red: four stream contract assertions, three request/recovery ownership assertions, one learner audit assertion failed against prior behavior. New control acceptance tests were authored before the component existed.
- Green: 274 frontend tests across 77 files; lint; production build; fixture exclusion; unchanged bundle budgets. CSS initially exceeded its cap by 773 bytes, then passed after shared-button reuse (0.90 kB new route CSS).
- Added hosted synthetic viewport/keyboard/error/recovery browser tests and a separate actual seeded source-only/persisted-reload journey.

## Resumed verification

The dedicated agent Chrome passed all seven answer-control scenarios at phone, tablet, desktop, landscape and 200 percent equivalent widths. The hosted Chromium, Firefox and WebKit suite passed 217 checks, including the 21 answer-control cases. Reviewed screenshots show readable stacked fields, visible keyboard focus, reachable actions and no horizontal page overflow. Long answer details scroll within the existing reading pane. Synthetic screenshots establish rendering and client behavior only.

Full CI passed on candidate `b0a3818`, including all 274 frontend tests and 15 authenticated product journeys. The new real-service test signed in as the seeded learner, posted a question, retrieved approved Homework Help Guide sources without a provider call, read the saved answer and reloaded it successfully. Authenticated browser traces, screenshots and video stay disabled. Local lint/build/budgets passed. Two unrelated local tests hit timing limits during the full parallel run and passed in a focused two-worker rerun; hosted tests passed without that limitation.

The initial full CodeAnt review completed. Its dispositions are in `issue-321-codeant-fix.md`. Follow-up changes restore test authentication state, name the existing cross-browser filter, scope component CSS and assert that recovery controls disappear after a saved answer. Final exact-head checks and review are required after these changes.

Design: `docs/product-design/assistant-answer-controls.md`. System review: `docs/operations/issue-321-system-review.md`. Rich record: `architecture-review-ai-answer-controls@1`.
