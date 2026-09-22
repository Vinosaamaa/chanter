# Browser integration findings (#339)

Initial hosted fixture run35785899984 at05c28e21 passed247 checks and failed six new assertions across Chromium, Firefox and WebKit. Three failures exposed native dialog initialization: React autofocus ran before showModal, leaving Search unfocused in real browsers despite the synthetic DOM test passing. Explicit focus now follows showModal. Explicit close runs before unmount so native focus restoration can complete.

The other three failures were an incorrect test expectation: the fixture has two notifications, so completing the first correctly selects the remaining row rather than focusing the empty Inbox heading. The assertion now expects that row. A separate one-notification browser fixture tests the actual last-item heading fallback. No browser assertion was suppressed, and the same three engines must pass the corrected scenarios.

Original component focus failures remain recorded by the focused red-to-green regressions. Actual layout, native focus and complete cross-browser acceptance remain hosted gates.

Second hosted fixture pass: 251 passed and five failed. Native dialogs deliberately permit focus to browser chrome while making the rest of the document inert; the test now verifies that boundary and explicit background focus denial. The last-notification fixture targeted the wrong URL: the owning client uses /api/v1/me/notifications. Corrected the fixture interception without changing notification behavior.

Full CI on 20b6de40 passed frontend/backend and both native release architectures. Engineering policy rejected the receipt title, which did not exactly match the PR title; corrected it. The newly enabled real browser projects exposed WebKit rejecting the Secure cookie on the plain-HTTP test origin, plus deliberate navigation cancellations reported as API failures by Firefox. Real cross-browser acceptance remains failed until the hosted HTTPS setup and faithful cancellation handling are verified. No cookie flags or network-error assertions have been weakened.

Hosted fixture1e791560 passed257 cases. New failures were a non-unique Create event
opener locator and incoming-call focus restoration after its previously focused
launch button becomes disabled. Scope the fixture opener to its actual toolbar;
preserve page focus before the call state disables its control. Add explicit
fixture readiness/focus checks before dispatching the synthetic incoming frame.
The hosted rerun remains required.

## Cross-browser follow-through

At 3134d03e, Firefox reported SEC_ERROR_UNKNOWN_ISSUER. The pinned Playwright Firefox provider bypasses distribution policies and reads PLAYWRIGHT_FIREFOX_POLICIES_JSON. Export the generated policy file through the hosted environment. Separate CA and leaf validation remain unchanged.

WebKit failures appeared while tests navigated immediately after the login response, before Home bootstrap completed. The helper now awaits four actual successful bootstrap responses and visible Home content. This is not yet a confirmed resolution; hosted execution remains the proof. A broader rule retaining every failed request until a later navigation was rejected because it could mask an earlier unrelated cancellation.

The call focus fixture showed that the real hook uses an ended phase before idle. Close the modal at ended, focus an available heading, and preserve busy/audio errors in the visible page. Two additional component regressions first failed for the missing alert, then passed after the fix.

## Delayed Inbox delivery and navigation ownership

The actual owner/member announcement journey at 50fc4123 found that delivery after
the initial Inbox request left the list empty indefinitely. The unread badge's
separate polling did not refresh that list. The OPEN query now refreshes every
15 seconds while mounted and foregrounded. Its focused regression first returned
an empty list, then delivered an item; it failed before the change and passes now.
The real journey also checks persisted read/completion state, but does not force
delivery to occur after mounting. Hosted execution remains required.

Independent review found that redirecting navigation accidentally took a second
snapshot of pending requests, admitting requests started after navigation began.
The redirect-chain regression reproduced that error. Redirects now retain the
first snapshot, and all five classification tests pass. HTTP/runtime errors and
ambiguous cancellation ownership remain fatal.

The 50fc4123 fixture suite passed 275 cases. Inspected Teaching screenshots at
1280 and 390 pixels confirm three-column desktop and two-column phone counts.
Those images prove layout at that checkpoint, not final integrated service behavior.

The 74d5c0a6 fixture suite passed, including footer scrolling. Its short-landscape
event screenshot showed the long title entering the absolute close control's
horizontal area. The event header now reserves space for that control. Browser
geometry assertions at all three dialog sizes guard the separation; hosted
confirmation is required for the new spacing.
