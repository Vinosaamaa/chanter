# System review: integrated product interaction (#339)

The owning behavior is browser navigation. No backend service, schema, credential or authorization contract changes. Focus follows the currently visible pane, using current item identity rather than private content in a selector. The last-item fallback keeps the list accessible when a completed notification disappears. Initial list loads do not steal focus.

The native Add friend dialog replaces manual keyboard handling and prevents background interaction. Search and request authorization continue through the existing hooks. Browser cancellation closes only this dialog. Cross-browser keyboard and screenshot evidence is required before acceptance; component tests alone cannot prove native focus containment or visual layout.

Hosted real product tests add Firefox and WebKit alongside Chromium. They preserve one worker, deterministic seeded resources and the existing authenticated artifact restrictions. Tests run on isolated CI services, not user accounts or production. The full final capability union is still required after lifecycle and recovery merge. Fixture evidence cannot stand in for real service delivery, real audio devices, live model-provider login or production hosting.

Remaining acceptance: manual screen-reader/browser-zoom review; production-like measured performance; actual provider configuration; final legal and retention review; complete recovery and public cutover under their owning issues. No public launch claim is made.

Teaching consolidation retains the existing dashboard API/hook and its operational counts. Query-preserving legacy redirection avoids a second dashboard implementation. Community forms share one native modal wrapper, with cleanup restoring an existing prior control. Incoming call Escape does not silently hang up; explicit decline/hang-up stays authoritative.

The event editor previously submitted HUB regardless of its toggle or prior scope. It now preserves visibility, courseId and cohortId on edit and accurately labels community-wide creation. Existing server validation/authorization remains unchanged. The ordinary-edit regression proves the request retains its restricted audience. The misleading toggle and its unused styles are removed.

Current external browser acceptance is blocked on hosted HTTPS: WebKit did not retain the Secure refresh cookie on the prior HTTP origin. Production cookie attributes must remain intact. Browser cancellation handling must distinguish document replacement from real API/network failures; generic error suppression is not acceptable.

The latest hosted run validates Secure-cookie behavior on WebKit, but real browser acceptance remains open for Firefox trust and navigation health. The patched Firefox policy path is explicit; production TLS is unaffected. Login tests now require complete successful Home bootstrap before further navigation. Busy/audio call errors outlive modal closure and the reset timer; starting or accepting a new call clears them through existing hook behavior.
