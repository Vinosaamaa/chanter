# UI control and evidence review

This is the active #254 audit, not a completion certificate. Fixture browser runs render production components against an explicit synthetic server. They do not establish backend behavior.

## Current integration pass

Issue #339 and PR340 own the remaining combined review. The reconstruction evidence
below is historical; it is not the final lifecycle/recovery release acceptance.
The current review started from accepted moderation and monitoring main, and now
includes accepted dependency PR338, owner cohort-invite authorization PR341 and
recovery infrastructure PR336. Final #251 and #342 integration remains open.

| Surface or capability | Current implementation and evidence boundary |
| --- | --- |
| Friends and Inbox phone navigation | Focus follows the visible pane and returns on Back. Component and three-engine fixture regressions pass for remaining/last notifications, Add friend, incoming/ended calls and persistent busy/audio feedback. Real announcement read/completion persists after reload; delayed delivery refreshes the OPEN list. |
| Questions | Conversation/composer focus and Back restoration pass focused and three-engine phone fixture checks. Provider answer quality remains a separate gate. |
| Teaching and legacy enrollment | Teaching retains metrics/Refresh and resolves bookmarked communities against accessible Study Servers. The old dashboard redirects. Enrollment resolves manager access and cohort bookmarks before privileged requests, retaining pagination and real Preview links. Focused regressions pass; phone/desktop count layouts were inspected. |
| Community dialogs | Event, announcement and invitation forms use native modality. Phone/landscape interaction and scrolling to the actual footer pass. Event edits preserve their restricted audience. Title/close-control separation now passes geometry checks at all dialog sizes and the landscape screenshot was inspected at 8cb80b93. |
| Community members and legacy header | #339 removes unavailable Active/Online filters and the inert Help control. All/Staff/Learners retain real API filtering and expose selected state. |
| AI answer controls | PR322 accepted explicit model/mode choices, billing notes and persisted answer audit. PR330 accepted scoped semantic retrieval. These do not establish live-provider login, paid entitlement or generated explanation quality. |
| Course Resources | PR318/325 accepted private storage/quarantine and supported ingestion. Final upload, authorization, processing and deletion journeys must include #251; actual remote bucket acceptance remains open. |
| Moderation | PR333 accepted operator/report/appeal controls and real signaling revocation, with phone/desktop and emailed-appeal browser evidence. Actual production operator enrollment remains open. |
| Billing | PR323 accepted truthful free-beta quotas and rejected quota elevation. No paid purchase or invoice control is offered for this initial release. |
| Account data | #251 owns real export/download, coordinated deletion and terminal receipt. Final role and browser integration awaits its accepted implementation. |
| Cross-browser product gate | At 74d5c0a6 all 45 actual-service journeys passed in Chromium, Firefox and WebKit, plus real moderation audio removal and emailed appeal. Latest-head and final accepted dependency-union checks remain required; prior passing results do not prove later changes. |

Native-dialog focus required a correction after the first actual browser run;
the synthetic DOM had not reproduced the initialization problem. The owning
debug record is `docs/operations/issue-339-debug-log.md`. This distinction is kept
explicit so future work does not treat component or fixture success as proof of
public product readiness.

## Reconstruction baseline

Candidate cbe0c128 passed [179 fixture checks](https://github.com/Vinosaamaa/chanter/actions/runs/34676290434) and [CI with 259 frontend tests, seven public journeys and fourteen real signed-in journeys](https://github.com/Vinosaamaa/chanter/actions/runs/34676290436). The fixture run covers 24 route views at six widths and selected Firefox/WebKit interactions. This evidence updates the individual rows below; untested capabilities remain explicit.

| Route family | Current control behavior | Evidence and remaining gate |
| --- | --- | --- |
| Public landing | Register/sign-in links, product/use-case navigation | Six-width fixture images and seven real public journeys passed; phone/desktop images inspected |
| Sign-in, verification, recovery | Real cookie session bootstrap, registration/verification, OAuth start, recovery and sign-out retry | Real registration, verification, recovery, reload and sign-out journeys passed at cbe0c128; phone sign-in rendered in three browsers |
| Shell and Home | Real Course links, unread count, mobile Browse, skip link, schedule actions, retry/new-account states | Six-width fixture images; drawer focus/Escape and Home reflow passed |
| Account devices | Real session list and revocation, native modal, explicit 15-minute remaining-access disclosure | Auth regressions and real session journeys passed; 390/844/1280 dialog screenshots, axe, Escape and focus-return passed; portrait/landscape images inspected |
| Course overview | Real cohort switching, sourced activity and schedule links, absent progress represented as unavailable | Phone/desktop images passed; clipped tabs now have scroll controls |
| Course chat | Real channels and realtime conversation; permission/error states; message-only composer | Phone composer visibility and landscape checks passed in Chromium, Firefox and WebKit fixtures; real delivery and calls require backend journeys |
| Questions | Phone list/detail/back, staff replies/moderation, AI action, citation excerpts and resource links | Author overlap and phone queue fixed; model/mode/cost and persisted audit integration depend on #248; assistant chips still require control review |
| Resources | Permission-gated upload, preview/download, search and type filters | Layout rendered; processing/scanning/deletion lifecycle waits for #244 integration |
| People and Course administration | Real roster, invitation/enrollment and staff management controls | Existing API hooks retained; owner/table/dialog screenshot expansion and #251 capability proof pending |
| Community announcements | Real announcement author/body/reactions; owner editing and invitation | Layout rendered; modal focus and mutation journeys still required |
| Community lounge | Real messages/profiles/timestamps, empty/loading/error/reconnect states | Production sample data and inert voice/attachment/emoji buttons removed; regressions and six-width screenshots passed; phone composer image inspected |
| Community events and discovery | Event RSVP/share/calendar, catalog filter/enrollment | Layout rendered; full interaction proof and wide/mobile states pending |
| Friends and direct messages | Real relationships, text messages and voice call lifecycle | Phone conversation layout rendered; focus return and reconnect/call/permission cases still under review |
| Inbox | Filter/read/done/source route actions; phone detail/back | Synthetic Mark done + return-to-list passed; real mutation journey required on final head |
| Calendar | Real date/filter/search/RSVP; named date grid with arrow, Home/End and Page navigation | Invalid ARIA grid corrected through a failing then passing regression; axe and six-width screenshots passed; real signed-in route loading passed |
| Teaching | Real Course queues, FAQ review, Office Hours and usage actions | Priorities compacted after actual review; six-width screenshots and real owner route loading passed; tablet/desktop images inspected |
| Billing | Real current quota/plan draft and save; honest absence of paid subscriptions/invoices | Fake settings controls removed; sourced usage retained; real plan behavior waits for #250 |
| Onboarding, legal and legacy management routes | Real join/create forms and legal text | Initial onboarding/legal screenshots exist; complete management-route reconstruction and dialog auditing remain open |

## Release gates still open

- Every role's visible control must have a successful action, truthful disabled reason, or useful recovery state. Inventory rows are not assertions that all controls passed.
- Browser images at 360/390, 768, 1280, 1920 and 3840px, portrait/landscape, long content, new accounts and error states require final review.
- Automated WCAG 2.2 checks supplement keyboard, screen-reader and actual browser-zoom verification. Density-adjusted viewport tests are only equivalent reflow evidence.
- Chromium, Firefox and WebKit must exercise the real signed-in core journey on the final merged dependency set.
- Route import budgets measure compressed assets. Production-like network performance and p75 Core Web Vitals require separate observation.
- #254 stays open until its dependency, review, merged-main and release conditions are met.
