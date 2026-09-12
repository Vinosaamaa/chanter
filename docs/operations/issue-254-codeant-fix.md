# Issue #254 CodeAnt review

PR: [#314](https://github.com/Vinosaamaa/chanter/pull/314). Initial full review completed for cbe0c128 on 2026-09-12. Quality scans are recorded separately and do not substitute for full review.

| Round | Finding | Assessment and action | Verification |
| --- | --- | --- | --- |
| 1 | Mobile marketing navigation stays visible after widening the window | Confirmed by CSS inspection: the narrow breakpoint sets grid display and the base selector did not hide the mounted navigation. Added base display:none while preserving the phone grid rule. | Added a real browser resize regression for Chromium, Firefox and WebKit; hosted result pending. Local browser execution remains unavailable, so no local failing browser run is claimed. |
| 1 | Native device dialog lacks scrolling for long lists | Existing native dialog behavior scrolled the landscape fixture. Made overflow:auto explicit instead of depending on browser defaults, and added a twelve-device landscape scenario that scrolls to the last device and disclosure. | Hosted long-list check pending in Chromium, Firefox and WebKit. |
| 1 | Calendar requestAnimationFrame is missing in jsdom | Not reproduced. Vitest's jsdom adapter enables pretendToBeVisual by default, which provides requestAnimationFrame. The existing test awaits and verifies focus after ArrowRight. No test shim or production change is warranted. | Calendar focus regression passed in the 259-test hosted suite at cbe0c128; focused local rerun recorded with round verification. |

The evidence documentation now records the green 179-fixture / 259-unit / seven-public / fourteen-real-signed-in result at cbe0c128 and distinguishes remaining issue and release gates. Final changed-head CI and re-review are required before integration.

Round-one local verification passed lint, all nine Calendar/session tests, production build and import-graph budgets. The Engineering projection check passed. Two unrelated Windows-only baseline Engineering tests fail on raw schema bytes after CRLF checkout and the scaffold's unsafe-prose case; the same suite passed in the hosted Linux gate at cbe0c128.
