# Integrated product interaction review (#339)

Repository: Vinosaamaa/chanter. Parent: #254. Lane: product integration and UI quality. Writer: root. Branch: codex/339-product-interaction. Intended PR: one draft linked to #339. Base: accepted dd6c900219473e42096937a733ca7b3f04603b20. Existing worktrees remain preserved.

## Acceptance and design

Keep the learning-desk-v3 design and the pinned frontend-design skill. This pass fixes actual interactions within its existing palette, type scale, responsive navigation and touch targets. Phone list/detail navigation must move keyboard focus into the visible pane and restore it to the chosen item on return. View changes must remain understandable without sight. Do not add another visual system or production fixture data.

Review current route controls against their owning API, then exercise real roles and inspect responsive renderings. Fixtures prove layout and interaction with synthetic data only. Actual browser journeys prove the tested service path. Manual screen-reader and browser zoom checks, real-device audio, measured production performance and provider acceptance remain distinct gates.

## Implementation plan

1. Inventory current controls and existing browser coverage. Reproduce concrete defects with focused behavior tests before implementation.
2. Fix owning components without enlarging initial asset budgets or changing authorization.
3. Extend hosted real signed-in coverage to Firefox and WebKit as well as Chromium. Preserve anonymous/public coverage and private authenticated artifact boundaries.
4. Integrate accepted #251, #332 and #337 before final union verification. Worker branches remain independently owned.
5. Publish one issue-linked PR with a rich engineering record, receipt, implementation notes and system review. Complete exact-head CI, full CodeAnt, required release checks and merged-main verification.

## Initial observations

- The real signed-in CI job installs Chromium only; Firefox/WebKit currently have fixture coverage, not the final real product journey.
- Friends mobile list/detail buttons change the visible pane without assigning focus to the newly visible pane. Verify with a failing regression before changing behavior.
- Accepted moderation and monitoring are now on main. Lifecycle and recovery remain in progress. No public deployment is claimed.

## First implementation checkpoint

Friends and Inbox focus regressions failed on the original behavior and now pass:
opening a detail pane focuses its heading, Back returns to the selected row, and
completing the last notification focuses Inbox. The focused suite has 12 passing
tests. Add friend now uses a native modal dialog instead of repeated document-level
Tab/Escape handlers; synthetic DOM tests cover cancellation, and actual hosted
browser tests cover focus containment, Escape, restoration and screenshots. The
offline friend group now states its actual label and count.

The real product configuration enumerates 42 tests across Chromium, Firefox,
WebKit and existing viewport projects. This listing confirms discovery, not
execution. CI installs all three engines for the signed-in job. Workflow syntax
validation passes. Hosted execution and final union verification remain pending.

The first build exceeded the existing core JavaScript budget. Native dialog
handling and consolidation of its duplicate/obsolete styles remove custom code
without raising core, CSS or capability allowances. All prior worktrees remain
preserved; no local preview server was started.

The control inventory also found an inert Help control in the legacy management
header and unavailable Active/Online member filters. These optional controls are
removed; the actual All/Staff/Learners filters retain their API behavior and expose
selected state to assistive technology. No member presence is fabricated.

## Teaching consolidation design

The legacy instructor dashboard duplicates the modern Teaching destination, but a
redirect alone would discard bookmarked Study Server selection, Refresh and useful
operational counts. Preserve those controls and existing dashboard fields first.
Keep the current Course/queue actions primary; show the additional counts in a
compact wrapping definition list using the existing typography and colors. Usage
must describe the lifetime free-beta limit. The old URL will preserve its query
when forwarding to Teaching; shared API/hook/types stay in place. Remove the unused
legacy page only after proving route and behavior parity, freeing shipped assets
for the accessibility fixes without increasing budgets.

## Community interaction corrections

The event list uses pointer-only articles, and community dialogs lack native modal
focus isolation. Use a small shared native-dialog wrapper for repeated community
forms, preserving their existing layout and mutation behavior. Event titles become
real buttons. Keep the underlying document inert and restore the previous control
on close. Verify phone, desktop and landscape layouts with synthetic screenshots.

The event editor also has a fixed historical date and a visibility toggle whose two
states both submit HUB. Default new events to a future hour; new community events
remain visibly community-wide. Editing must preserve the existing visibility,
Course and Cohort identifiers. A regression must fail before this correction.

Teaching now preserves bookmarked Study Server selection, Refresh, all six operational counts and lifetime free-beta usage. The legacy route forwards its full query to Teaching inside the modern shell. Four Teaching and ten Friends component tests pass. Incoming voice-call dialogs use native modality and restore the prior control; this does not prove audio delivery.

Event title buttons, native community dialogs, future defaults and restricted-audience preservation have three red-to-green component regressions. Nine event/community tests pass. Removed only the inert legacy Mark helpful control; modern saved-answer helpful behavior remains. Lint/build and unchanged bundle caps pass at this checkpoint (core JS 1264.0 KiB raw / 377.8 KiB gzip, CSS 214.4 KiB raw / 34.9 KiB gzip). Additional browser cases cover 390px phone, 1280px desktop and 844px landscape dialogs; hosted proof remains pending.
## Hosted cross-browser transport correction

The first real WebKit run did not retain the Secure refresh cookie on HTTP and
failed after reload. Add a second job-owned HTTPS listener to the existing hosted
Caddy fixture, trust its ephemeral certificate in the actual Chromium/Firefox/
WebKit environments, and keep HTTPS validation enabled. Set public email links to
that same origin before Java starts. Preserve the separate HTTP/audio proof and
check its delivered appeal link against the configured public origin.

Navigation cancellation classification will require an observed replacement
navigation for an in-flight request from the prior document. Ordinary API failures,
HTTP errors and connection failures remain fatal. No generic CORS or console-error
suppression is authorized by this diagnosis.
## Legacy enrollment boundary

Keep the manager enrollment screen's server pagination, UUID search, enrolled dates,
selectable invite link and custom-channel Preview links. Resolve navigation and
canManagePeople before mounting its manager hooks. Visible non-manager Courses
forward to their contextual People tab with query parameters preserved. Loading or
unavailable navigation must never issue invite/roster requests. Replace the legacy
fabricated Unassigned column with a link to the real People assignment workflow.
## Hosted HTTPS and residual interaction checkpoint

The hosted browser origin now has a separate ephemeral CA and signed server leaf.
The CA is installed for the actual Chromium NSS database, bundled Firefox policy,
OS/WebKit trust and Node API clients. Caddy serves HTTPS on9420 while existing9419
moderation audio remains unchanged. Email links use the configured HTTPS public
origin; the appeal test verifies that origin without rewriting a delivered link.
Browser certificate checking and Secure/HttpOnly/Strict cookie assertions remain
active. Hosted execution is the acceptance gate; local syntax checks prove no TLS
behavior.

Five browser-health tests pass. Requests interrupted by navigation are classified
only after the current successful document replacement commits. Failed navigation,
ordinary API aborts, HTTP errors and real connection failures remain failing. The
classifier records failures until teardown so event ordering cannot prematurely
suppress a request.

Three legacy enrollment regressions pass. Manager hooks mount only after resolved
canManagePeople; non-managers keep their cohort query when sent to People. Manager
pagination, cohort switching, UUID search and custom-channel Preview links remain.
The fabricated Unassigned column was removed in favor of the actual assignment
workflow in People. Incoming call focus preserves the last page control when an
incoming call disables that control before the modal mounts. The related Friends
and enrollment tests total13 passing; lint/build and unchanged asset caps pass.

## Error feedback and focus follow-through

Questions now focus the conversation or new-question composer and restore the originating control on Back. Inbox failed read/completion actions show an actionable error; a failed completion retains the selected notification for retry. Incoming calls close at the ended phase and focus the visible conversation heading when the prior launch control is disabled. Busy/audio errors remain in the visible pane until the next call attempt, rather than disappearing with the modal or a short reset timer.

Component regressions cover all three pages. The synthetic browser completion test fails its first request and verifies a successful retry. Community screenshots were inspected at phone width and landscape height: forms and event details are readable, with modal scrolling retained. Raw event audience enums were replaced with member-facing labels.

The prior checkpoint passed both native release stages and the application unit/build gates. Synthetic browser coverage passed 266 cases; six incoming-call focus cases require this correction. Real signed-in coverage still failed Firefox certificate trust and WebKit navigation health. Playwright's patched Firefox requires its explicit policy environment variable; the generated policy path is now exported. Login helpers wait for the actual Home bootstrap responses and rendered content before navigating onward. No error class, certificate check or cookie attribute is suppressed. Hosted validation of this checkpoint remains required.
## Integrated announcement and Inbox acceptance

The next hosted journey publishes through the actual owner announcement form, signs out, signs in as a member, opens the delivered Inbox item, marks it done and reloads. It checks persisted read/completion state against the real notification service. It uses unique synthetic content in the disposable product stack, with authenticated traces, video and screenshots disabled. Listing discovers the new case in all three product browsers; execution remains required.

Actual Teaching screenshots at 390 and 1280 showed the six operational counts wrapping as five plus one on desktop. The grid now uses three columns at ordinary desktop widths, six on wide displays and two on phones. The next screenshot set must confirm that adjustment.
## Review and remaining browser correction

The first full CodeAnt review is recorded in issue-339-codeant-fix.md. It identified cohort/server bookmark resolution and overly broad request ownership; targeted regressions now cover those corrections. The native dialog footer is explicitly scrollable and exercised on short screens. The Inbox test-order claim was checked against actual passing execution and does not reproduce.

At a5485e7d, all 275 synthetic browser cases passed. The real suite passed 40 cases; only Inbox-to-Calendar hard navigation failed on WebKit and retried on Firefox. The journey now follows its visible links, retaining strict failure reporting and the separate reload/deep-link cases. All Firefox trust and real registration/recovery cases passed with certificate validation active. Final current-head and capability-union acceptance remain open.

The new actual-service announcement journey at 50fc4123 exposed delayed delivery:
an announcement arriving after the Inbox's initial fetch never appeared without a
reload. The mounted OPEN list now refreshes every 15 seconds while foregrounded.
An actual QueryClient regression failed before the correction and passes after it;
it delivers the notification only after the first empty response. Lint, production
build and unchanged asset budgets pass. Hosted browser confirmation is still
required. The completed list does not poll, and account-scoped cache keys and
server authorization remain unchanged.

At 74d5c0a6, all 45 actual-service product journeys passed across Chromium,
Firefox and WebKit, including owner publication and persisted Inbox completion.
The actual moderation/audio-removal/email-appeal journey also passed. Synthetic
responsive coverage passed, and independent inspection of six additional phone
and landscape screenshots found no new blocker. One event-title/close-control
spacing correction from screenshot review still requires hosted confirmation.
Final #251 and #342 integration remains open; these results do not authorize launch.

## Account-data integration

The accepted-main integration at c58db625 passed full hosted application checks,
synthetic responsive checks and both native release architectures. The RTP
observation correction retains counters for ended stream reports without hiding
continued packet growth; the auth recovery journey waits for successful Home
bootstrap before leaving the page.

The account menu now opens #251's export page and native ZIP download flow. A
separate account-deletion route handles preparation, ownership blocks, expiry,
cancellation, typed irreversible confirmation and post-sign-out read-only status.
It never treats acceptance or missing status as completed deletion. Recent-login
navigation retains the opaque request ID. Independent review found cross-job stale
responses, unreadable 202 confirmation and expired-cookie cached-status issues;
all are corrected with focused regressions. The design and budget boundaries are
in account-deletion-interaction.md. Twenty-five account/auth checks, lint, build
and budget checks pass. Thirty-three cross-engine account-data fixture cases are
listed; their execution and pixel inspection remain pending for this new scope.
Backend #251 and recovery #342 must land before final actual-service acceptance.

The first hosted account-data run passed 296 cases and failed twelve confirmation
transitions, consistently redirecting to sign-in before the lazy receipt mounted.
The mounted receipt now consumes a document-bound session-generation handoff and
keeps its own query while removing private cache. Startup skips session refresh on
receipt reload; the existing cross-tab marker closes local access. Real data-router
regressions cover the transition and an account switch, and stale history state
cannot sign out another document's session. Export/confirmation screenshots at
phone, desktop and landscape were independently inspected with no visual blocker.
The fixed transition and reload still require hosted confirmation.
