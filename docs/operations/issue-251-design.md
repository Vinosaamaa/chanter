# Account data lifecycle

Repository: Chanter. Owning issue: #251. Branch: `codex/251-account-lifecycle`. One draft pull request will contain the implementation and Engineering receipt. The starting accepted main is `935f6f849ae72c3682ed26d033aa543a9be0b4d8`. #249 owns moderation authority and auth migrations V5–V7; this lane starts at auth V8. #247 owns agent V13; lifecycle agent migrations start at V14. Final integration follows acceptance of both dependencies.

## Outcome and acceptance

An authenticated person can export their data in a machine-readable archive, see which service supplied each section, and download it through a private expiring route. Account and Study Server deletion show each downstream step and any retention or replication condition that prevents completion. A confirmation requires a live session created by a recent login. Passive refresh cannot renew that proof. Owned Study Servers require an explicit transfer or deletion choice before account removal can proceed.

The implementation must prove private export access, actual cross-service coverage, transaction rollback, duplicate delivery, process restart, late-write fencing, source/index/file disappearance, and truthful failed or pending states. Operator recovery uses existing durable-event replay, not direct database edits. The issue remains open through accepted-main and deployed verification, real support/legal review, and external retention proof.

## Data ownership map

| Owner | Personal records and export boundary | Deletion boundary |
| --- | --- | --- |
| Auth | Profile, linked provider names, session metadata, the person's lifecycle jobs. Never passwords, token hashes, reset links, provider tokens, or operator-only evidence. | Close login and live session authority first; redact identity and credentials after prerequisites. Keep minimal terminal authority and restricted retention evidence. |
| Community | Memberships, owned Study Servers/Courses/Cohorts, invitations, authored announcements/events, participation, and actual plan records. Free-beta plan state is not an invoice. | Resolve ownership explicitly; fence membership/creation writes; preserve descendant identifiers until all participant receipts exist. |
| Message | The person's authored content, support questions/replies, eligible direct-message history, friend requests and blocks. Exclude third-party private and operator-only evidence. | Redact or remove scoped rows and revoke conversation access; preserve only explicit preservation holds. Publish existing search deletion events. |
| Media | Uploaded-file metadata and available, authorized clean bytes. Explain unavailable/quarantined content rather than exporting unsafe or inaccessible files. | Reuse durable resource deletion state and object deletion retry. Object/index disappearance is a required receipt, not inferred from an HTTP acknowledgement. |
| Agent | The person's questions, saved answers, feedback, and usage/provenance. No provider credentials or another learner's prompts. | Redact snapshots and answers, invalidate pending native work, and reuse resource terminal fences. Preserve conservative attempt authority without source text. |
| Search | Derived documents only; identify the source and avoid claiming an independent canonical copy. | Terminal scope exclusion must precede asynchronous index removal and reject stale updates. |
| Notification | Notifications addressed to the person and delivery metadata. | Remove scoped content and fence delayed deliveries. |
| Analytics and realtime | Currently derived/live state rather than separate durable personal databases; report actual coverage explicitly. | Revoke live access, terminate presence/calls where owned, and invalidate derived/cache state. |

## Export boundary

Auth owns the public job and download authority. Source services own explicit projections of their data. Queries name every exported field and bind the account identifier; neither a browser nor an event supplies SQL, table names, filenames, or storage keys. The archive uses stable generated entry paths, UTF-8 JSON Lines and a manifest with schema version, source capture times, counts, digests, omissions, and expiry. It is a collection of service snapshots, not a distributed atomic database snapshot.

Use bounded chunks and streaming archive output so one export cannot load an account's entire history into memory. Snapshot pages remain private in their owning database and expire after 24 hours. A job is downloadable only after every required participant has a committed receipt. Partial, oversized, expired, or failed work is visible and never labeled complete. Concurrent export and deletion serialize through terminal account authority; deleting an account invalidates pending exports and download access. Active-job limits and expiry bound retained work.

The initial source store uses 256 KiB pages, a 256 MiB snapshot limit, a 1 GiB retained-source limit, and at most sixteen live snapshots per service with one per account. These limits reject work explicitly rather than truncating it. Archive entry paths are generated from fixed section names or resource UUIDs. A source transaction commits the snapshot and receipt together. Terminal account cancellation removes retained pages and prevents delayed capture from recreating them.

The existing durable outbox transports fixed lifecycle requests and receipts. The existing consumer transaction commits each source snapshot or deletion step with its receipt event. Duplicate delivery is idempotent. The existing operator replay mechanism is the only delivery retry mechanism. Timed cleanup only expires private snapshots and completed job payloads; it does not start a parallel retry queue.

Auth accepts at most five export creations per account in a rolling day, including cancelled work, with sixteen active jobs per instance database. It retains job progress metadata for thirty days after expiry; source pages expire after twenty-four hours and the cleanup poll runs every five minutes. Cancellation closes public download access immediately and exposes outstanding source cleanup receipts separately. Public creation requires a live durable session created by login within five minutes. Password login and the verified OAuth callback create new sessions; refresh preserves the original creation time.

Download uses fixed private source URLs, bounded manifest/page reads, a three-second connect timeout and a fifteen-second whole-fetch deadline, and four concurrent download slots per auth process. It checks live session and job authority before and after each source page, plus a ten-minute processing deadline between pages. Each manifest must match its committed source receipt and account/job/expiry; every entry's exact byte count and SHA-256 are checked. A failed or revoked stream never writes a ZIP central directory. The archive explicitly describes its separate source capture times. These byte and concurrency limits are application bounds, not a guarantee about infrastructure capacity or client network speed.

Browser and mobile download must stream to the browser's download manager without assembling an account-sized JavaScript Blob. A bearer-authenticated, CSRF-protected POST issues an opaque handle in a Secure, HttpOnly, SameSite=Strict cookie scoped to one job's download path. Only a hash is stored, bound to the exact user, live session, job and original access expiry. One unused handle per job is replaced on issuance and expires within sixty seconds. GET atomically consumes it once, then checks the bound live session, original access expiry and current job between pages. A fixed job URL contains no credential; no bearer is placed in history, query parameters or browser storage. New issuance cannot revive a cancelled job. This path supplements the existing bearer download for clients and remains subject to the same four-stream admission and archive integrity checks.

Issuance and consumption also require the browser's current refresh cookie to identify the same session. An old job cookie cannot authorize a download after the browser switches accounts or sessions. The gateway delegates only the exact GET download path, with a canonical UUID and no query, to auth's grant check. It removes caller-supplied identity headers as usual. Other methods, authorization issuance, metadata routes, ambiguous paths and unrelated APIs retain their existing JWT requirement. Failed or interrupted navigation consumes the grant and requires a new user-triggered issuance.

The account-data UI belongs in the existing settings shell, with one left-aligned title, a readable explanation of archive scope, the current request and its actual source progress, then previous requests. Use Chanter's Instrument Sans and existing ink, muted, blue, border and status colors. Keep the main action adjacent to its scope explanation; put source details in a disclosure rather than seven decorative cards. Mobile uses the same order in one column, with full-width controls where necessary. Pending, unavailable, cancelled and expired work remain distinct. Download feedback says that the browser download was requested, not that a local file was verified. Refresh is user-triggered, and a recent-login requirement explains why signing in again is needed.

The UI palette is ink `#192c46`, muted `#596a80`, line `#dce3ec`, action `#2458d3`, error `#b42332`, success `#197451`. Instrument Sans uses the existing settings heading and body scale. Everything is left aligned. A compact sidebar identifies account settings; the main column keeps explanatory lines below eighty characters. Source progress uses a semantic list inside one disclosure, with text states and a native progress element. This avoids implying independent source actions or billing purchases. The initial plan's separate source cards were removed because seven equal cards would hide the one actual user decision: request or download the archive.

The approved exact lazy AccountDataPage budget is 16 KiB raw / 6 KiB gzip JavaScript and 3 KiB raw / 1 KiB gzip CSS, implemented as decimal byte ceilings 16000/6000 and 3000/1000. The first production build measured 9340/3400 bytes JavaScript and 2100/710 bytes CSS, rounded to ten bytes. Existing core/shared/vendor and initial-route caps remain unchanged. The final budget union must isolate only this exact entry and prove its absence from actual initial landing, sign-in and Home transfers.

```text
Desktop: Settings | Account data
         Account  | What this archive contains
                  | Request export       Refresh status
                  | Current request: Preparing / Ready / Unavailable
                  | > Source details     Download / Cancel
                  | Previous requests
Mobile: Account data
        Scope and expiry explanation
        Request export
        Refresh status
        Current request, source disclosure, actions
        Previous requests
```

The download action issues its cookie, then activates one owned anchor with the HTML download attribute and the fixed same-origin attachment route. Cross-origin API configuration is rejected for browser archive download. This delegates bytes to the browser without changing the production policy that forbids embedding Chanter pages. The page reports only that the download was requested and directs the person to their browser's download status. Retrying requests a new grant. Account changes clear the owned anchor, cancel pending UI actions and prevent late responses from starting a download. Hosted desktop/mobile browser proof is required; a unit test does not prove download-manager behavior.

Received conversations, available file bytes, notifications containing source content, and saved AI answers use generated entries bound to an owning access scope. The source checks its current conversation/resource/answer permission before and after reading a retained page. A protected entry has no permissive fallback when its owning access checker is unavailable. Revocation after capture makes that archive unavailable; a new export can explain the now-omitted content. Authored records and personal metadata have explicit separate projections so a peer's content never enters an unprotected section.

Protected chunks bind at most one hundred exact source IDs each, with at most ten thousand protected items per snapshot. Current source checks receive batches of at most one hundred IDs. Saved answer and received-content scopes also bind an expected content digest so a later redaction cannot leave older text reachable through an otherwise still-authorized record ID. File scopes bind the verified file digest. The source capture checks its thirty-second work budget between bounded operations and before committing; the source executor also interrupts blocked work at that deadline. Limits produce an explicit failure rather than silently truncating the archive.

Source execution admits one capture per process and at most four waiting delivery callers. Only identical active events share work; other events receive a busy response before entering the database. Delivery waits at most three seconds, below the existing outbox's five-second HTTP limit. A virtual thread executes capture with a thirty-second interruption and transaction deadline. Four retained reads may run concurrently, each with a ten-second deadline covering both current-authority passes. Capacity is released only after the worker's transaction has committed or rolled back and released its connection. No source retry queue is added. JDBC export projections set a three-second statement timeout, and cancellation/deadline authority is checked again before commit. Slow network and database tests must prove cleanup; caller HTTP cancellation alone is not that proof.

The final schema integration follows foundation #252 epoch 7, retrieval #247 epoch 8, moderation #249 epoch 9, then lifecycle #251 epoch 10. Intermediate migration gaps exist only in isolated development tests. The final branch must contain the accepted earlier migrations before release; out-of-order Flyway execution is not a fallback.

## Deletion and preservation

A terminal account or Study Server marker closes access before fan-out. Each participant retains that authority while cleanup progresses, rejecting delayed events and writes that would recreate data. The coordinator stores service-specific progress, safe error codes, preservation holds, and the revision needed for off-host recovery. Human confirmation explains irreversible steps; no undeclared grace period or restoration promise is implied.

Preservation holds are explicit operator actions under #249 authority, with scope, reason, review/expiry information, and immutable audit. A held payload stays restricted and is reported as retained; it must not remain reachable through normal APIs. Billing retention applies only to records that actually exist. No statutory retention period or legal basis is invented by this implementation.

## Recovery journal contract

Auth recovery applies the canonical journal and participant prefix in one transaction. An ACCOUNT entry revokes its session families, consumes outstanding email verification/reset tokens, cancels retained exports and their download handles, and expires queued email payloads for that account. Identity payload cleanup remains PENDING until the preservation policy and owning cleanup complete. STUDY_SERVER and RESOURCE have no auth-owned payload; their auth participant records terminal authority with COMPLETE cleanup while downstream owners still report their own state. Restored-session invalidation is a separate idempotent operation bound to the exact applied and canonical watermark. Its durable receipt commits with revocation of every restored browser session, refresh token, unused email token and download handle. Recovery must keep public writers closed throughout reapply and invalidation; a receipt does not authorize opening ingress or prove external replication.

Auth owns an append-only terminal journal for ACCOUNT, STUDY_SERVER and RESOURCE targets. Entries contain only a monotonic committed revision, event UUID, validated target UUID/kind, terminal action and timestamp. Bounded private export pins an upper watermark and includes a deterministic digest. Reapply is idempotent and reports each participant's committed watermark. A restore invalidates all browser sessions and pending native requests rather than replaying every historic logout.

#252 owns external journal storage/checkpointing and restore orchestration. A current off-host checkpoint must precede a claim that deletion is complete. Missing replication remains a visible pending condition. Catastrophic host loss cannot recover newer tombstones from a lost source database. Recovery remains isolated and nonpublic until the current journal has been validated and reapplied everywhere; a backup alone never proves preservation of later deletions.

## Retention and legal truthfulness

Production backup policy currently keeps two successful weekly pgBackRest full chains and required WAL, with daily incremental backups. Expiry depends on successful replacement and retention execution; it is not a guaranteed fourteen-day deletion deadline. Restic configuration archives have no completed chain-aware pruning yet. Real provider operation, recovery point and recovery time remain unverified. These are explicit deployment facts and gaps, not guarantees.

Existing placeholder mailboxes, unverified domains, and unsupported effective/review dates must not be presented as working legal/support contacts. Publish only deployment-provided, reviewed contact and disclosure configuration. When missing, say that the deployment has not supplied it. Describe actual cookies, analytics and AI/provider behavior without claiming legal approval. Real legal review and support ownership remain external launch gates.

## Implementation order

1. Add the bounded export/snapshot contract, auth job authority and real source projections with transaction and access tests.
2. Add terminal journal and participant lifecycle receipts, integrating #249 live authority and #246/#247 deletion fences after their accepted commits.
3. Add ownership resolution, recoverable deletion progress, preservation and retention automation, then private export/download and mobile account controls.
4. Verify cross-service failure/replay, stale writes, binary/index disappearance, restore reapply, current legal disclosures and actual browser pixels. Publish one draft PR with full CI/security/review and retain external gates explicitly.
