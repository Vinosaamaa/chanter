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
The corrected account transition passes hosted cross-browser responsive checks;
phone, landscape and desktop screenshots have been inspected. Actual export/deletion acceptance requires
the accepted #251 backend and #342 recovery union, not intercepted fixture APIs.

The first hosted confirmation cases exposed the real router's lazy-loading race.
The public receipt closes the originating session only after mounting, using a
generation number and document-lifetime nonce in transient navigation state. It consumes that state, retains
its own query and removes private cached data. No account identity or credential
is carried. Receipt reload skips auth restoration; leaving for sign-in restores
normally when required. This also preserves another account that signed in while
the public route was loading. Full data-router regressions cover both outcomes.
The nonce rejects history state surviving reload even if another account reuses
the same numeric auth generation. It carries no credentials or account identity.

## Study Server and course-file progress

The tested #251 contract at 6f7a70ee now supports requester-bound source progress.
This extends the same #339 issue, worktree and PR. Existing server DELETE and new
resource DELETE controls retain the returned job UUID after HTTP 202. Their owning
services close access immediately; cleanup and recovery acknowledgement remain
pending. An authenticated `/app/deletions/:jobId` route reads auth's source-deletion
endpoint independently of the removed graph. Only the original requester may read
it. A 404 can mean the durable request has not reached auth yet; refresh must not
replace the job or claim completion. No deadline is promised.

Keep server deletion behind owner authorization and resource deletion behind both
workspace and source management capabilities. Use a native confirmation dialog
with clear irreversible wording, cancel/focus behavior and visible busy state.
Ignore responses after account, course or dialog identity changes; reuse the same
target after uncertain submission. Route to progress after an accepted response,
invalidate the server/resource list and retain a return link to the surviving
picker or course list. Progress needs explicit refresh, unavailable and preserved
record states, mobile/landscape keyboard evidence and actual-service union proof.
Use the existing blue/ink/white settings typography and 44-pixel actions; add no
decorative metrics or simulated progress percentage.

The source status entry has a separate 8,000 raw / 3,000 gzip JavaScript cap and
shares the existing capped deletion stylesheet. Confirmation code and styles stay
in the unchanged core budget. The dialog reuses the existing padded modal surface
inside native modality. Resource page state remounts on account, session generation,
server or course change, preventing an old confirmation from reopening after return.
Two parent-context regressions reproduced this defect before the correction.
Source deletion browser fixtures remain synthetic, with actual requester/retry and
cleanup proof required from the combined #251/#342 backend.

Before final integration, a manually dispatched hosted dependency preview composes
the current UI/scripts with the entire backend at the explicitly pinned #251 commit
5a6e92e07b68ab7073e0ed5df48ecd028c29d3b6. It verifies the infrastructure trees agree,
uses no production secrets, publishes no release, and leaves ordinary exact-head
checks unchanged. This narrower preview can prove export, cancellation, authority
revocation and receipt reload while source cleanup remains ERASING. It is not the
final #251/#342 union. The browser scenario stays preview-gated until that union is
accepted, and validates a complete seven-source ZIP without logging its contents
or cookie values. Existing trace/video/screenshot restrictions apply.

A separate source case uploads a real scanned file and verifies its bytes for the
owner and enrolled member. It submits API deletion, waits for durable registration,
then opens, refreshes and reloads the requester progress page. Same-target retries
must return the original job even after canonical registration; non-requesters and
anonymous clients cannot read its progress. It deliberately does not claim browser
dialog-to-registration timing, immediate completion, or restored-source proof.

## Previously displayed question content

An authoritative answer 404 removes the cached answer and its citations. A successful
reply snapshot replaces previous replies, including content from a removed account.
These reads apply independently, so one failing endpoint cannot retain content that
the other endpoint has confirmed absent. Failed reads keep their prior state and
show the existing error. A successful local answer update or reply completed after
the read began survives that older read; a subsequent fresh read remains authoritative.
The protection belongs to one active question load and ends when it settles or is
cancelled. It does not compare timestamps or preserve historical cache rows forever.
