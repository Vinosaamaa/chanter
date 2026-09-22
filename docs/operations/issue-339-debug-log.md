# Browser integration findings (#339)

Initial hosted fixture run35785899984 at05c28e21 passed247 checks and failed six new assertions across Chromium, Firefox and WebKit. Three failures exposed native dialog initialization: React autofocus ran before showModal, leaving Search unfocused in real browsers despite the synthetic DOM test passing. Explicit focus now follows showModal. Explicit close runs before unmount so native focus restoration can complete.

The other three failures were an incorrect test expectation: the fixture has two notifications, so completing the first correctly selects the remaining row rather than focusing the empty Inbox heading. The assertion now expects that row. A separate one-notification browser fixture tests the actual last-item heading fallback. No browser assertion was suppressed, and the same three engines must pass the corrected scenarios.

Original component focus failures remain recorded by the focused red-to-green regressions. Actual layout, native focus and complete cross-browser acceptance remain hosted gates.

Second hosted fixture pass: 251 passed and five failed. Native dialogs deliberately permit focus to browser chrome while making the rest of the document inert; the test now verifies that boundary and explicit background focus denial. The last-notification fixture targeted the wrong URL: the owning client uses /api/v1/me/notifications. Corrected the fixture interception without changing notification behavior.

Full CI on 20b6de40 passed frontend/backend and both native release architectures. Engineering policy rejected the receipt title, which did not exactly match the PR title; corrected it. The newly enabled real browser projects exposed WebKit rejecting the Secure cookie on the plain-HTTP test origin, plus deliberate navigation cancellations reported as API failures by Firefox. Real cross-browser acceptance remains failed until the hosted HTTPS setup and faithful cancellation handling are verified. No cookie flags or network-error assertions have been weakened.
