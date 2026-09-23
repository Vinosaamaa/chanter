---
schemaVersion: 1
id: architecture-review-product-interaction
revision: 1
type: architecture-review
status: proposed
title: Integrated product navigation and browser acceptance
repository: chanter
capabilityIds: ["production-deployment"]
createdAt: 2026-09-22
reconstructed: false
confidence: high
unknowns: ["Final lifecycle and recovery union", "Hosted cross-browser and visual acceptance", "Manual assistive technology and production performance"]
modules: ["frontend-friends", "frontend-inbox", "frontend-account-data", "product-browser-tests"]
interfaces: ["keyboard-list-detail-navigation", "hosted-product-browser-gate", "account-lifecycle-request-and-receipt"]
seams: ["visible-pane-to-keyboard-focus", "fixture-to-real-service-evidence", "revoked-session-to-read-only-receipt"]
adapters: ["playwright"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Accessible list-detail navigation", "Cross-browser signed-in journeys"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Integrated product interaction review", "url":"https://github.com/Vinosaamaa/chanter/issues/339", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["frontend/src/features/v2-shell/pages/FriendsPage.test.tsx", "frontend/src/features/v2-shell/pages/InboxPage.test.tsx"]}
visibility: public-safe
publicationEligibility: eligible
issue: 339
pr: 340
release: null
run: null
---
# Integrated product navigation and browser acceptance

Friends and Inbox hid the list on phones while keyboard focus remained on its hidden row. Return navigation left focus on the hidden Back control. Focus now follows the visible reading heading and returns to the selected row, with a heading fallback when the last item disappears. The native Add friend dialog provides an inert background, platform focus handling and Escape cancellation, replacing duplicated document-level key handling. Existing role, relationship and notification services remain authoritative.

The real signed-in browser gate previously installed Chromium alone. Firefox and WebKit now run the same product tests against the actual hosted service stack, one worker at a time. Synthetic screenshots remain separate layout evidence. Authentication traces, screenshots and video remain disabled in real account tests. Nothing in this change configures a provider, relaxes authorization or declares public launch.

Design follows learning-desk-v3 and the repository's pinned frontend-design skill. Existing core, initial-route and prior capability budgets remain unchanged. New account export/deletion entries have separate explicit caps; shared code remains in core. Focus behavior has red-to-green component regressions; final screenshot, hosted, review and combined-release acceptance remain open.

Teaching resolves bookmarked Study Server selection against accessible communities, retaining Refresh, dashboard metrics and lifetime free-beta usage while its legacy route becomes a query-preserving alias. Enrollment resolves bookmarked cohorts only after manager authorization. Community event titles are keyboard controls; repeated editor/details forms use native modality, scroll on short screens and restore focus. Event edits preserve the original audience identifiers, correcting a request that always sent HUB. These changes have focused red-to-green regressions.

The first expanded real-browser run exposed a plain-HTTP origin incompatible with
WebKit Secure-cookie persistence. The hosted stack now uses a trusted ephemeral
HTTPS origin. Real registration and recovery pass across all three engines;
production certificate and cookie checks remain intact. Navigation cancellation
classification retains only the pending requests known before navigation started,
including across redirects. Ambiguous ownership remains a failure.

The real announcement journey exposed delivery after the mounted Inbox's initial
fetch. A foreground OPEN list now refreshes every 15 seconds through the existing
authorized query. User-scoped cache keys and mutation invalidation remain intact;
completed lists do not poll. A focused regression establishes the delayed-fetch
case. The service journey checks owner publication and member read/completion
persistence, but does not force delivery timing. Current-head browser and final
accepted lifecycle/recovery union checks remain required.

The account-data contract comes from #251. Export uses the native browser download
manager after a short-lived HttpOnly authorization grant. Deletion separates
preparation and typed irreversible confirmation. A public, cookie-only status
route remains reachable after ordinary authentication is revoked. Uncertain
confirmation reads that same receipt; missing authority never implies deletion
completion. Request controllers are keyed by account, auth generation and job,
and abort on unmount. Sign-in preserves the explicit opaque job URL. Independent
review findings have focused regressions; actual erasure and recovery proof stay
with #251/#342 and cannot be inferred from intercepted browser fixtures.

Study Server and course-file deletion now preserve accepted asynchronous request
IDs in a requester-bound progress route. Native confirmation explains irreversible
access closure, while missing progress, retained records and pending recovery remain
distinct. Resource page state remounts on account/session/course changes to discard
stale confirmations. The source status JavaScript has an 8,000 raw / 3,000 gzip cap;
it shares existing deletion CSS, and dialog dependencies stay within core limits.
The prior account receipt checkpoint passed all 308 hosted responsive cases, full
application checks and both native release architectures. Source progress fixtures
and final real-service union acceptance are still outstanding.

The Study Server picker now mounts the existing responsive shell, correcting
legacy-shell modal styling exposed by hosted screenshots. A manually gated hosted
dependency preview combines this UI with an exact #251 backend tree and requires
identical infrastructure. It proves a narrower real account journey without
publishing or replacing final union acceptance. Archive validation is bounded and
checks its central directory, CRCs and seven-source coverage; receipt assertions
cannot print credential values. The preview remains excluded from ordinary checks
until the lifecycle backend is accepted.
