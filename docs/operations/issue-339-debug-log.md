# Browser integration findings (#339)

Initial hosted fixture run35785899984 at05c28e21 passed247 checks and failed six new assertions across Chromium, Firefox and WebKit. Three failures exposed native dialog initialization: React autofocus ran before showModal, leaving Search unfocused in real browsers despite the synthetic DOM test passing. Explicit focus now follows showModal. Explicit close runs before unmount so native focus restoration can complete.

The other three failures were an incorrect test expectation: the fixture has two notifications, so completing the first correctly selects the remaining row rather than focusing the empty Inbox heading. The assertion now expects that row. A separate one-notification browser fixture tests the actual last-item heading fallback. No browser assertion was suppressed, and the same three engines must pass the corrected scenarios.

Original component focus failures remain recorded by the focused red-to-green regressions. Actual layout, native focus and complete cross-browser acceptance remain hosted gates.
