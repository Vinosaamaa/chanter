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
polling. This adds at most four list requests per minute per mounted foreground
query. The completed list remains event/refocus driven. User-scoped cache keys,
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
