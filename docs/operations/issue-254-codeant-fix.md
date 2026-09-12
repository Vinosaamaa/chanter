# Issue #254 CodeAnt review

PR: [#314](https://github.com/Vinosaamaa/chanter/pull/314). Initial full review completed for cbe0c128 on 2026-09-12. Quality scans are recorded separately and do not substitute for full review.

| Round | Finding | Assessment and action | Verification |
| --- | --- | --- | --- |
| 1 | Mobile marketing navigation stays visible after widening the window | Confirmed by CSS inspection: the narrow breakpoint sets grid display and the base selector did not hide the mounted navigation. Added base display:none while preserving the phone grid rule. | Added a real browser resize regression for Chromium, Firefox and WebKit; hosted result pending. Local browser execution remains unavailable, so no local failing browser run is claimed. |
| 1 | Native device dialog lacks scrolling for long lists | Existing native dialog behavior scrolled the landscape fixture. Made overflow:auto explicit instead of depending on browser defaults, and added a twelve-device landscape scenario that scrolls to the last device and disclosure. | Hosted long-list check pending in Chromium, Firefox and WebKit. |
| 1 | Calendar requestAnimationFrame is missing in jsdom | Not reproduced. Vitest's jsdom adapter enables pretendToBeVisual by default, which provides requestAnimationFrame. The existing test awaits and verifies focus after ArrowRight. No test shim or production change is warranted. | Calendar focus regression passed in the 259-test hosted suite at cbe0c128; focused local rerun recorded with round verification. |

The evidence documentation now records the green 179-fixture / 259-unit / seven-public / fourteen-real-signed-in result at cbe0c128 and distinguishes remaining issue and release gates. Final changed-head CI and re-review are required before integration.

Round-one local verification passed lint, all nine Calendar/session tests, production build and import-graph budgets. The Engineering projection check passed. Two unrelated Windows-only baseline Engineering tests fail on raw schema bytes after CRLF checkout and the scaffold's unsafe-prose case; the same suite passed in the hosted Linux gate at cbe0c128.

Round one at 7b7ea8d passed [all 185 fixture checks](https://github.com/Vinosaamaa/chanter/actions/runs/34676740605), including menu resize and long-list scrolling in all three browsers. The full re-review completed and returned the following findings. The next candidate rebases onto main at 259bf41, which includes the portable Engineering checks.

| Round | Finding | Assessment and action | Verification |
| --- | --- | --- | --- |
| 2 | Google provider assertion queries a button instead of a link | Corrected the role and added a configured-provider positive case using the real navigation link. Provider lookup is mocked explicitly. | Sign-in tests passed alongside Friends, 12 tests total. |
| 2 | Invalid friend deep link can hide the phone list with no active friend | Require an active friend before applying the phone conversation state. | New regression failed before the change and passed afterward. |
| 2 | Back retains the friend URL parameter | Back removes only the friend parameter with replacement navigation and returns to the list. | New regression failed before the change and passed afterward; browser Back/reload coverage added for all three browsers. |
| 2 | Visual timestamps depend on host clock and timezone | Use one fixed synthetic UTC timestamp, a matching fixed browser date, en-US locale and UTC browser timezone. Runtime timers remain active. | Hosted screenshot run pending; these controls affect only explicit visual tests. |

Round-two local verification passed lint, all twelve focused Sign-in/Friends tests, production build and budgets, and all 34 portable Engineering tests. The already-tracked contract files were refreshed to their exact LF bytes after the rebase picked up the new attributes; this produces no schema diff. The previous application head 7b7ea8d also completed [full CI](https://github.com/Vinosaamaa/chanter/actions/runs/34676740603) successfully.

Round two at f745c48 passed [188 fixture checks](https://github.com/Vinosaamaa/chanter/actions/runs/34677099276) and [full CI](https://github.com/Vinosaamaa/chanter/actions/runs/34677099322). Full review completed with two findings, handled in the third and final remediation round.

| Round | Finding | Assessment and action | Verification |
| --- | --- | --- | --- |
| 3 | Selecting a friend retains the prior URL | Selection now updates the friend query parameter while preserving other parameters, so reload/share identifies the selected conversation. | Focused regression observed failing before the fix; the existing three-browser reload case now checks selection too. |
| 3 | Home excluded from Firefox/WebKit subset | The subset was documented, but Home is a useful missing core view. Included existing populated/empty Home cases at 390 and 1280px without expanding all route screenshots to every browser. | Final hosted results pending. |

The remaining #248 integration requirement is recorded on that issue: EOF before an authoritative complete event must report interruption and clear partial output with source-only recovery. Current code clears partial text without persisting it, but does not expose EOF recovery. This reconstruction does not claim that backend-dependent gate is complete.

Round-three local verification passed all nine Friends tests, lint, production build/loading budgets, the Engineering projection check and diff whitespace checks for the changes.
