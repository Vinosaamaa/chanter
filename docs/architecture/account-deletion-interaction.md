# Account deletion and receipt interaction

Repository: Vinosaamaa/chanter. Owning UI issue: #339 / PR340. Backend authority:
#251 / PR335. Lane: integrated product interaction. Writer: root, on
codex/339-product-interaction in the registered issue-339 worktree. Existing work
is preserved. The UI cannot be accepted before the backend contract is accepted.

Reuse #251's existing account export page, API, native download flow and lazy
bundle allowance. Add account deletion as a separate deferred route. Follow the
pinned frontend-design skill and learning-desk-v3: readable settings content,
plain state descriptions, visible keyboard focus and full-width phone actions.
Do not add immediate deletion to resource or Study Server screens until their
public progress contract is available.

Preparation checks ownership and expires without confirming deletion. The client
generates one opaque UUID, also the job ID, and reuses it after an uncertain
response. Keep this ID in the URL for reload recovery; store no credentials.
Only the authenticated preparation screen can request, confirm or cancel.
Confirmation requires the exact phrase DELETE MY ACCOUNT and a recent login.
Show irreversible access closure and possible restricted-record retention before
the confirmation action. A 202 response means accepted, never completed.

The independent receipt route stays reachable after account revocation. It uses
same-origin credentialed GET without the ordinary refresh client. An opaque,
path-bound HttpOnly cookie supplies read-only authority; JavaScript never reads
it. After confirmed deletion, clear ordinary client authentication and navigate
to this public receipt view. An uncertain confirmation also leads to receipt
inspection rather than creating a replacement job or declaring success.

States distinguish preparation, blocked ownership, cancellation/release,
irreversible cleanup, pending external recovery acknowledgement and completion.
PRESERVED source records remain explicitly retained. Delivery failure requires
attention and does not count as completion. No deadline or legal approval is
invented. A missing/expired receipt is an unavailable receipt, not proof that
deletion completed or failed.

Use one in-flight action and cancel requests on unmount/account change. Fence
stale completions by the current auth generation, account and job. A job URL change
remounts the request controller and aborts its old actions. Preserve the same job
through explicit recent-login sign-out and unauthenticated receipt navigation.
Provide explicit
refresh and recovery from 409, 428, 429 and uncertain network responses.

Acceptance: focused regressions for duplicate/uncertain requests, account changes,
cancel/confirm exclusion, cookie-only receipt access and truthful state copy;
phone/desktop keyboard and geometry evidence; actual revoked-session receipt and
native export journeys after #251 integration; unchanged protected-route budgets.

The implementation reuses #251's export API and native browser download flow.
Account deletion has its own lazy entry, capped at 14,000 raw / 5,000 gzip bytes
of JavaScript and 3,500 raw / 1,200 gzip bytes of CSS. Export retains its existing
16,000 / 6,000 JavaScript and 3,000 / 1,000 CSS allowance. Only exact entry assets
are separated; shared code remains in the unchanged core budget. Both stylesheets
are forbidden from the initial public, sign-in and Home dependency graphs.

Independent review identified and corrected cross-job late responses, unreadable
successful confirmation responses and stale receipt data after cookie expiry.
Focused account/auth regressions, lint, production build and budget checks pass.
Cross-browser responsive fixtures are authored but remain unverified until hosted
execution and screenshot inspection. Actual export/deletion acceptance requires
the accepted #251 backend and #342 recovery union, not intercepted fixture APIs.
