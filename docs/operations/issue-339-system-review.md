# System review: integrated product interaction (#339)

The owning behavior is browser navigation and integration of the #251 account-data contract. No backend service, schema, credential or authorization contract changes. Focus follows the currently visible pane, using current item identity rather than private content in a selector. The last-item fallback keeps the list accessible when a completed notification disappears. Initial list loads do not steal focus.

The native Add friend dialog replaces manual keyboard handling and prevents background interaction. Search and request authorization continue through the existing hooks. Browser cancellation closes only this dialog. Cross-browser keyboard and screenshot evidence is required before acceptance; component tests alone cannot prove native focus containment or visual layout.

Hosted real product tests add Firefox and WebKit alongside Chromium. They preserve one worker, deterministic seeded resources and the existing authenticated artifact restrictions. Tests run on isolated CI services, not user accounts or production. The full final capability union is still required after lifecycle and recovery merge. Fixture evidence cannot stand in for real service delivery, real audio devices, live model-provider login or production hosting.

Remaining acceptance: manual screen-reader/browser-zoom review; production-like measured performance; actual provider configuration; final legal and retention review; complete recovery and public cutover under their owning issues. No public launch claim is made.

Teaching consolidation retains the existing dashboard API/hook and its operational counts. Query-preserving legacy redirection avoids a second dashboard implementation. Community forms share one native modal wrapper, with cleanup restoring an existing prior control. Incoming call Escape does not silently hang up; explicit decline/hang-up stays authoritative.

The event editor previously submitted HUB regardless of its toggle or prior scope. It now preserves visibility, courseId and cohortId on edit and accurately labels community-wide creation. Existing server validation/authorization remains unchanged. The ordinary-edit regression proves the request retains its restricted audience. The misleading toggle and its unused styles are removed.

The first hosted browser attempts exposed WebKit rejecting the Secure refresh cookie on HTTP. The disposable hosted origin now uses trusted HTTPS without changing production cookie attributes. Browser cancellation handling must distinguish document replacement from real API/network failures; generic error suppression is not acceptable.

Hosted registration and recovery now pass in all three engines with certificate validation active. The patched Firefox policy path is explicit; production TLS is unaffected. Login tests require complete successful Home bootstrap before further navigation. Complete browser acceptance remains open for the latest navigation and Inbox corrections. Busy/audio call errors outlive modal closure and the reset timer; starting or accepting a new call clears them through existing hook behavior.

The review tightened request ownership further: requests beginning during a pending navigation are ambiguous and receive no old-document cancellation allowance. Only the pre-navigation snapshot can qualify after a successful replacement. Enrollment resolves bookmarked cohorts within authorized navigation before mounting manager hooks. Teaching resolves server bookmarks within the accessible-server list before any dashboard request. These do not change backend authorization.

Delayed announcement delivery exposed a mounted Inbox consistency gap. Its OPEN
list now polls the existing authorized endpoint every 15 seconds; React Query's
default background behavior and observer lifecycle prevent hidden or unmounted
polling. This schedules four periodic refreshes per minute per mounted foreground
query; retries, focus and invalidation can add requests. The completed list remains event/refocus driven. User-scoped cache keys,
request cancellation and server authorization are unchanged. Real browser delivery
and persistence must pass before this is considered accepted.

Account export and deletion use separate lazy routes and the existing #251 API.
Preparation is reversible; typed confirmation closes access and is irreversible.
The public receipt reads through its path-bound cookie without bearer credentials
or refresh. Network loss and unreadable accepted confirmations lead to the same
receipt, never another deletion job. Missing receipts do not establish completion.
Account/generation/job changes abort and fence prior action responses. Explicit
reauthentication preserves the opaque job URL; ordinary logout still clears its
return destination. Failed receipt refresh hides stale current-status claims.
The independent review's three findings have direct regressions and are fixed.

Export and deletion entry assets receive explicit separate caps; shared imports
stay in the unchanged core budget and protected initial route graphs cannot load
their CSS. Browser fixtures cover narrow phones, landscape, desktop, keyboard
confirmation, native ZIP downloads, expiry and missing receipt states. They prove
layout and interaction only. Actual lifecycle/recovery proof remains #251/#342.

The receipt must mount before auth is cleared: resolving a lazy navigation does
not establish a committed React route. It consumes a credential-free generation
handoff, clears only that originating session and its private queries, and retains
its own status fetch. Startup never refreshes a revoked session on this receipt
route. The existing cross-tab sign-out marker propagates local access closure.
Hosted confirmation failures reproduced with the actual data router; independent
account-change and reload regressions guard the correction.

Source deletion progress is requester-bound authenticated data, separate from the
cookie-only account receipt. A new job may initially return 404 while its durable
request reaches auth. The UI retains the same job and never infers completion from
absence. Restricted records and recovery acknowledgement remain visible. Confirmations
bind to the opening account/generation/target; course navigation discards pending
resource modal state. Same-target retry depends on the backend returning the original
request after terminal access closure. Review identified that dependency in media;
#251 must prove it before integrated acceptance. Browser fixtures cannot establish it.

The hosted account dependency preview restores the exact pinned backend tree only
after proving infrastructure agreement. It has read-only repository permission,
explicit branch/input gates, no artifact upload and no publishing path. Selecting
the preview input also excludes the main release-package job. Its native download
check reads a bounded archive in memory and validates ending, CRCs and seven-source
manifest coverage without printing data. Credential assertions project only safe
flags or booleans. Final accepted-union verification remains separate.

The actual account dependency preview passed its three-engine lifecycle journey.
Its broader WebKit usage case retried after leaving Teaching while a cohort's
Office Hours request was still pending. Teaching must distinguish pending schedule
lookup from an authoritative empty schedule; the usage journey must wait for that
visible state before navigating away. Browser-health failure classification stays
strict. The existing server-home page also retains unique owner course/cohort and
enrollment actions, so moving it into the responsive shell must preserve those
actions rather than replacing it with a non-equivalent catalog redirect.

Receipt authority is checked again on every mount even if the query cache is fresh;
cached completion is hidden during the request and after a failed response. Losing
either course or source management permission discards the selected file before a
later permission restoration. Three regressions reproduced these gaps before their
fixes. Server-home keeps its original API and owner actions under the existing v3
tokens and responsive shell; no catalog or enrollment authorization is broadened.

The next bounded source acceptance case uses actual upload/scanning, source DELETE
and requester/stranger APIs, then opens and reloads the real progress page. It waits
for durable registration through API polling before browser navigation because an
immediate progress GET may legitimately be absent. This proves the real status
contract and access closure separately from the fixture-confirmed dialog submission;
it must not claim end-to-end dialog-to-registration timing. Browser health remains
strict, and no transport interception, mocked response or manual database relay is
used. Pending cleanup must remain pending until the source and recovery owners prove
completion. Only fresh synthetic accounts/data are used, without credential artifacts.

Post-loop review identified a launch-blocking wrong-target reply path: history refresh
could replace a missing selected question while preserving a staff draft, then submit
that draft to another question. The correction must distinguish initial selection
from explicit selection, preserve intent through refresh and bind staff drafts to
their account/session/channel/question before submission. It must preserve the text
without silently reassigning it. This is required interaction correctness, not another
general cosmetic remediation round. Learner explicit-new-question selection must also
survive pending history, and asynchronous data changes must not steal composer focus.
