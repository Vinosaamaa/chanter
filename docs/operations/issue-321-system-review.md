# Issue 321 system review

The Questions page consumes the authenticated channel model catalog. The agent service remains the authority for course access, assistant grants, model availability, provider usage, and saved-answer replay. The browser keeps only the currently selected model/mode and sends them as explicit query parameters when the learner invokes an answer. It stores no provider credentials and offers no subscription entitlement.

The SSE parser handles retrieval status, draft tokens, structured errors and the authoritative completion event. End of stream without a dispatched completion rejects as interrupted. Reader cancellation and lock release run on parsing, callback and transport failures. A question stream owns an abort controller; superseding, navigating or unmounting invalidates that ownership. Old callbacks and old promise settlement cannot append text or clear the current request.

Once a provider-capable request has started, this mounted Questions workspace conservatively limits that question to explicit source-only recovery. Even a failure before provider transport may require source recovery in this UI. The durable backend generation ledger remains the cross-reload and cross-tab authority that prevents a second provider call after an uncertain attempt. Source-only recovery sends both `modelId=source-only` and `answerMode=source-only`; it is never automatic. A saved replay is labeled from its returned audit, never from the current selector.

The new controls use the existing learning-desk tokens and buttons. Phone and tablet selects are 16px with 44px controls. Question list/detail navigation and the separate learner question composer remain intact. Hidden FAQ and TA management requests are gated by the already loaded course capabilities. Source chips are descriptive list items; actual resource links remain the navigation controls.

## Verification boundary

Observed local red-to-green regressions cover incomplete streams, structured status/error handling, selection encoding, superseded streams, navigation cancellation, restricted provider retry, and learner-visible saved provenance. The complete frontend suite passes 274 tests; lint, production build, fixture exclusion and the unchanged bundle budgets pass. The new route CSS is 0.90 kB raw; the initial static JavaScript dependency graph is 108.8 KiB gzip.

Hosted browser fixtures cover model/mode selection, saved audit, EOF and structured-error recovery at phone, tablet, desktop and landscape widths. These are synthetic response/layout checks, not provider or persistence evidence. A separate authenticated test uses the actual seeded backend, approved course resource retrieval, saved-answer read and reload. Both hosted suites and full CI are pending on the initial candidate. Browser artifacts are disabled for authenticated tests; only labeled synthetic fixture screenshots are retained.

Live provider accounts, semantic explanation quality, production retrieval and deployment remain unverified. Grounded explanations remain unavailable under the backend catalog. Native subscription delivery belongs to issue 316. Issue 321 stays open through its required merged-main and release checks.
