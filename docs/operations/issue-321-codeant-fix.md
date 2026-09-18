# Issue 321 review dispositions

The initial full CodeAnt review completed against `b0a3818` in PR #322. No confirmed security, authorization or data-loss defect was found.

- Provider attempts recorded before completion: retained intentionally. The browser cannot determine whether an aborted or failed request reached the provider. Conservative source-only recovery is the documented contract; the backend ledger remains authoritative across reloads. Restoring provider retries based only on transport failure would weaken that protection. A future safe retry requires an explicit server guarantee that generation never started.
- Recovery controls allegedly remain after success: not reproduced. Completion updates the question status, so `canAskAi` hides answer controls. The real source-only/reload journey passed, and the synthetic recovery tests now explicitly assert that neither recovery controls nor provider selectors remain.
- Stale documentation: corrected current status and labeled the preserved pause checkpoint as historical. Verification prose distinguishes fixture rendering from real persistence and unverified production capabilities.
- Test authentication leakage: restore the initial authentication store after each Course Questions test.
- Cross-browser test filter and CSS scope: named the existing filter and scoped answer-control styles under the application shell. Test tags for the whole existing visual suite are outside this slice.
- Attempt-set cleanup and cloning: retain attempt markers for the mounted workspace so revisiting an interrupted question cannot offer another provider request. The set is released on unmount. A capped history would discard the safety fact; there is no measured retention problem here.
- Token batching, owner-tool extraction and shared sign-in helpers: deferred. These propose broader performance or structural changes without a demonstrated failure in this bounded UI integration. Provider output is already bounded by the backend, and browser tests passed.
- Positional deferred-request test handles: retained. The test intentionally starts two requests in order and verifies that late settlement of the first cannot replace the second. The ordering is the behavior under test.

Verification includes focused Questions tests, local lint/build/budgets, seven dedicated-Chrome answer scenarios, hosted cross-browser checks and the actual seeded source-only answer/reload journey. Follow-up exact-head CI and full review remain required before root integration.
