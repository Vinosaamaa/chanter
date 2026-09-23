# Current route and control review (#339)

Reviewed against the router and control owners at `8914546`, with actual owner
setup added at `2904c3f9`. This replaces the
historical #103 list for launch review. A route being present or a screenshot
passing does not establish that every mutation works against deployed services.
The final accepted lifecycle/recovery union and public environment remain open.

## Evidence boundaries

`frontend/e2e/product-critical.spec.ts`, `auth-lifecycle.spec.ts` and
`assistant-answers.spec.ts` exercise actual services. The lifecycle/source cases
require the explicitly pinned #251 dependency preview. The standalone moderation
journey also exercises real audio revocation and emailed appeals.

`frontend/e2e/visual/` uses synthetic accounts and responses. Its route-family
matrix covers 360, 390, 768, 1280, 1920 and 3840 pixels in Chromium; selected
interaction cases also run in Firefox and WebKit. Account-data cases add 320-pixel
layouts. Accessibility scanning, keyboard assertions and screenshots have separate
roles. Equivalent narrow-width reflow is not manual browser zoom or screen-reader
acceptance. All 341 fixture cases passed at the reviewed head.

## Routes and controls

Paths below are relative to `/app` unless they begin with `/`. Dynamic IDs denote
the authorized server, course, channel or job selected by the current account.

| Route family | Controls and owning behavior | Observed evidence and remaining gate |
|---|---|---|
| `/`, `/sign-in`, `/forgot-password`, `/reset-password`, `/verify-email` | Landing navigation; registration, verification, sign-in, recovery and sign-out use the auth feature. | Public browser journeys plus real verification/recovery and revoked-session checks. Actual public SMTP delivery remains open. |
| `/oauth/callback/google` | Completes the configured Google authorization flow. | Provider configuration and actual provider login remain open; no OAuth success is inferred from password sign-in. |
| `/terms`, `/privacy` | Public policy content and navigation. | Responsive fixture coverage. Final wording must agree with #251 retention and the deployed providers. |
| `home`, `welcome`, `picker` | Authorized server/course navigation and onboarding entry points. | Real owner/member/learner Home and navigation; responsive populated/empty fixtures. |
| `onboarding/join-or-create`, `onboarding/create-study-server` | Join by code; create-server wizard, Back and Close use onboarding APIs. | Responsive route fixtures; copied-invite joining has component coverage. Actual server creation passes in all three engines. Team invitations, Back/Close and final invite/join acceptance remain distinct. |
| `servers/:serverId/home` | Create arbitrary course/cohort names, open course channel and manage enrollment for non-enrolled owners. | Nine three-engine owner fixtures cover submission, results and preserved links. Actual course/cohort form submission and its resulting card pass in all three engines. |
| `servers/:serverId/courses/:courseId/enrollment` | Authorized enrollment, invite copy, cohort selection and teaching-assistant link. | Actual owner manual enrollment, refreshed roster and learner access pass in all three engines. Capability/URL tests and accepted owner invite authorization remain separate. Final browser invite/copy/join remains open. |
| `teaching`, `instructor-dashboard` | Server selection, Refresh, operational counts, course and queue links. Legacy bookmark redirects with its query. | Component and fixture bookmark coverage; real Usage journey waits for the Office Hours lookup. Loading and failure cannot masquerade as an empty schedule. |
| `inbox` | Open/Done lists, detail, completion, Back, retry and foreground refresh. | Real announcement delivery and persistent completion; phone focus, disappearing final item and failed-completion retry fixtures. |
| `calendar` | Previous/next month, Today, filters, keyboard day selection, event RSVP and destination links. | Component behavior and six-width fixtures; real navigation smoke. Full real event/RSVP lifecycle remains an integrated acceptance item. |
| `friends` | Friend requests, list/detail navigation, direct messages and calls. | Real page load; phone Back, native Add friend dialog, incoming-call focus and persistent call errors. Real-device audio, TURN and provider/network conditions remain separate. |
| `settings/usage`, `settings/billing` | Refresh and factual free-beta usage. Billing bookmark redirects to Usage. | Real owner usage and rejected quota escalation; empty/exhausted/error fixtures. No checkout or paid upgrade is promised. |
| `account-data` | Request/cancel export, expand source progress and native ZIP download. | Actual seven-source archive/download preview plus native browser-download fixtures. Provider/final-union proof remains open. |
| `account-data/delete`, `/account-deletion/:jobId` | Prepare, cancel, type confirmation and reload a cookie-only signed-out receipt. | Actual lifecycle preview; fresh receipt authority and account/session boundary regressions. Complete erasure is not established by access revocation or a pending receipt. |
| `deletions/:jobId` | Requester-only source progress and Refresh after resource/server confirmation. | Real scanned bytes, access closure, original-job retry, requester isolation and progress reload. Fixtures cover the dialog. Immediate dialog-to-registration timing and complete source cleanup remain open. |
| Course `overview`, `chat`, `people` | Course/cohort navigation, channel conversation, member filters and authorized management actions. | Six-width fixtures; readable reconnect state, clipped-tab controls and capability tests. Final real role mutations and reconnect delivery remain integrated gates. |
| Course `questions` | Ask/reply, select/filter/refresh, assistant provider/model controls, helpful vote, moderation and queue handoff. | Real approved-source answer and reload; three-engine draft/focus and interrupted-answer fixtures. Drafts cannot silently move to another question. Actual provider login/inference, subscription execution and final staff mutations remain open. |
| Course `resources` | Upload/status/content and authorized deletion. | Real upload/scanning/content and deletion API proof with browser progress; confirmation/permission-loss fixtures. Final visible upload form and complete physical cleanup remain separate. |
| Course `office-hours` | Authorized schedule and audio-room controls. | Responsive fixtures and readiness state; moderation journey proves specific audio revocation. Real devices and public connectivity remain open. |
| Course `settings` | Save details, add the first cohort, assign instructor, publish/unpublish and archive through governance APIs. There is no delete control here. | Source-reviewed owner gate and lifecycle API unit tests; visible settings mutations remain unverified. |
| Community `announcements`, `lounge`, `events`, `discover`, `members` | Publish/edit, live conversation, event form/RSVP, course discovery and member navigation. | Real announcement/search/Inbox persistence, consumer restart and native keyboard-dialog fixtures. Event edit retains its existing restricted audience. Other real mutations remain in final integrated acceptance. |
| `safety`, `/operator`, `/appeal` | Report, authorized moderation, appeal and reversal. | Accepted real moderation/audio/email journey; actual production operator enrollment remains open. |
| Legacy study/course channels, summary, support operations | Existing bookmarked conversation, summary and staff-operation destinations remain routed through their owning modules. | Channel-keyed remount and session response guards reviewed. Final bookmark/control walkthrough remains open; no unreachable-route assumption removes these checks. |

The `/app` and `servers/:serverId` index routes redirect to Home. `/dev/demo` is
development-only and excluded from production; it is not a launch destination.

Global search has real persisted announcement evidence and synthetic multi-width
type selection. Device sessions have phone/desktop list and short-screen scrolling
fixtures; actual auth tests verify rotation and revocation. Their production-provider
and final integrated checks still apply.

## Intentionally unavailable actions

Send is unavailable while its request is pending or input is invalid. A retained
staff draft whose original question disappeared stays readable but cannot be sent
to another question. Provider answers cannot silently retry after an uncertain
attempt; explicit source recovery remains available. Export download requires a
ready, unexpired export; deletion confirmation requires the prepared job and exact
typed phrase. Missing authority, expired receipts and failed requests never display
successful completion. These are state or permission boundaries, not placeholder
buttons.

Removed inert Help/member-presence controls have no launch dependency. Paid billing
is outside this free beta. All remaining provider, manual and real-mutation gates
above must be resolved or explicitly scoped before #255 public cutover.
