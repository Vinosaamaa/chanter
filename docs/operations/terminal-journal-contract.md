# Terminal recovery journal

Owning issue: #251. Deployment replication and restore gating: #332 under #252. This is a private service protocol. Do not expose these routes through the public gateway.

Auth migration V8 stores the terminal journal and checkpoint. The migration must follow accepted #249 auth V5–V7; no deployment may run the intermediate branch with those migrations missing and later depend on Flyway out-of-order execution.

## Export and checkpoint

All routes use `X-Internal-Service-Token` with the auth service's configured internal credential. Responses have `Cache-Control: no-store`. The credential is not a browser token, export download credential, or proof that external storage succeeded.

`GET /api/v1/internal/lifecycle/journal?after=0&limit=100` returns schema version 2, `after`, `through`, `entries`, and `next`. Each watermark is `{revision,digest}`. The first request pins the latest committed `through`. Subsequent requests pass `after=<previous next.revision>&through=<pinned through.revision>`. Page size is 1–500. Stop only when `next` exactly equals `through`. An empty journal has revision 0 and a digest of 64 zeroes.

Each entry contains `revision`, `eventId`, `targetKind`, `targetId`, `action`, `deletedAt`, `retentionPolicy`, `previousDigest`, and `digest`. Target kind is ACCOUNT, STUDY_SERVER, or RESOURCE. Action is DELETE. UUIDs identify targets; no email, profile, content, session token, evidence, or storage credential is included. A target appears once. Allocation and the owning terminal mutation share a database transaction and a serialized head lock. Rollback cannot create a missing revision or publish later authority before an earlier transaction commits.

The digest is lowercase hexadecimal SHA-256 of UTF-8 text, with one newline after every field, including the last:

```text
2
<revision as decimal>
<eventId UUID>
<targetKind>
<targetId UUID>
DELETE
<deletedAt as Java Instant.toString(), millisecond precision or coarser>
PRESERVE_MODERATION_RECORDS_V1
<previousDigest>
```

The required retention policy is `PRESERVE_MODERATION_RECORDS_V1`. It retains already collected, access-restricted moderation reports, bounded evidence snapshots, case notes and audit records. It does not turn an ordinary resource object into preserved evidence or claim a legal basis or expiry date. Existing evidence snapshots contain bounded text and identifiers, not original file bytes. Source erasure must not be described as complete preservation of the original file. There is no generic hold creation or release API in this contract.

Verify schema, fixed target/action/policy values, UUIDs, contiguous revisions, previous digest, every entry digest, pinned upper watermark, and the final digest. Missing policy and version 1 pages fail validation; this unreleased protocol has no legacy deployment migration. `TerminalJournal.Page.validate()` implements the same checks for Java participants. A digest is an integrity chain, not an external signature or storage attestation. The external replica manifest and participant receipts remain schema 1 and bind this version 2 digest.

After writing and verifying the entire prefix in independently recoverable external storage, #252 posts `POST /api/v1/internal/lifecycle/journal/checkpoint` with `{revision,digest,checkpointId}`. `checkpointId` is an opaque UUID assigned to that durable checkpoint. Auth rejects a revision beyond its committed head, a mismatched digest, or a different identity for the same latest checkpoint. Identical retries succeed. Older acknowledgements return the newer recorded checkpoint. `GET /api/v1/internal/lifecycle/journal/checkpoint` returns `{checkpoint:null}` until the first acknowledgement.

Auth records the trusted recovery caller's acknowledgement. The caller must not acknowledge a local-only file, incomplete object, unverified write, or storage that would disappear with the source host. Deletion remains pending until this acknowledgement covers its terminal revision. No provider or external replication proof is supplied by the application test suite.

## Restore integration

Recovery must obtain the current independently stored journal and verify its chain. A checkpoint remembered in a restored database does not prove it is the current external checkpoint. Missing, discontinuous, older-than-required, or unverifiable authority keeps the deployment isolated and nonpublic. Replaying a database backup alone is insufficient.

Auth recovery additionally imports the original canonical entries and head through `TerminalJournalStore.restore`, in the same transaction as its participant mutations. The lock order is canonical head, participant authority, then owning user/session rows. This matches normal deletion. Existing entries must have identical digests; newer entries retain their original IDs, timestamps and chain. A failed participant mutation rolls back the canonical import too. Later appends extend the recovered chain rather than forking from the older database backup. Import does not manufacture an off-host checkpoint acknowledgement. #332 must compare the canonical watermark with the current verified external prefix before reopening writers.

The participant reapply store is implemented as `TerminalReapplyStore`. Its schema records a committed contiguous prefix, permanent target authority, and source cleanup state. A page repeats the exported `TerminalJournal.Page` contract. The source checks the supplied starting watermark against its committed prefix, validates every repeated digest, and applies each new entry with its actual source mutation in one transaction. Ordinary durable deletion delivery can establish the same target authority before recovery replay reaches that revision; it does not claim that intervening journal entries have been applied. Conflicting target identity, revision or digest is rejected. A failed page rolls back all new target fences, source mutations, cleanup events and prefix advancement.

A participant receipt is `{schemaVersion:1,source,authority:{revision,digest},pendingTargets,preservedTargets}`. The counts cover targets through the applied authority prefix. They do not report physical deletion completion when cleanup remains pending or restricted moderation records remain preserved. A completed target cannot return to a pending state. Source writes must take the same terminal-authority lock before their rows and reject terminal targets, preventing in-flight writes from recreating deleted content.

Auth implements `POST /api/v1/internal/lifecycle/journal/reapply` with that page body and `GET /api/v1/internal/lifecycle/journal/reapply/receipt`. Both require its internal credential. The POST accepts at most 256 KiB of JSON and 500 entries, rejecting duplicate or unknown properties. ACCOUNT effects revoke session families, consume unused email tokens, expire queued email payloads, cancel export jobs and erase retained export/download data in the same transaction as original-chain import and participant receipt. Identity payload cleanup remains PENDING. Auth has no Study Server or resource content to clean; it still records their terminal authority.

Notification implements the same two private routes with its own credential. ACCOUNT removes that recipient's notifications and retained export payloads; STUDY_SERVER removes notifications in that server; RESOURCE removes notifications whose source is that resource. Derived notification payloads have no independent moderation evidence retention. Ordinary notification upserts take the same authority lock, then discard deliveries targeting any terminal account, server or resource. Durable delivery still acknowledges those stale events so a retry cannot recreate them. Reapply, source deletion, snapshot cancellation and receipt advancement share a transaction. Tests cover rollback, repeated private delivery and an ordinary writer overlapping terminal commit. Community, message, media, agent and search reapply handlers remain unfinished; the common store alone does not purge or protect their restored data.

Auth also implements `POST /api/v1/internal/lifecycle/recovery/invalidate-sessions` with `{recoveryId,authority:{revision,digest}}`. It accepts at most 2 KiB of strict JSON. The exact canonical head and participant prefix must both match before every restored browser session, refresh token, unused email token and export navigation handle is invalidated. Queued email payloads are expired. A durable receipt commits with those effects: `{schemaVersion:1,source:"auth",recoveryId,authority,invalidatedAt,scope:"ALL_BROWSER_SESSIONS"}`. An identical retry returns the original receipt and time; changing authority under the same recovery ID is rejected. A receipt cannot be obtained against a stale prefix after later journal appends.

Agent implements that same invalidation endpoint using its own internal credential and scope `ALL_PENDING_NATIVE_REQUESTS`. It locks the applied participant head and requires an exact authority match. ISSUED and ACCEPTING native requests become REJECTED and lose their evidence in the same transaction as the receipt. A later claim or settlement cannot accept those rows. Already settled rows and generation usage remain unchanged, including UNKNOWN usage and conservative reservations. Tests exercise actual migrated native rows, rollback, process-recreated replay, stale authority, private HTTP and malformed requests. Agent's terminal reapply mutations remain unfinished, so this endpoint cannot yet prove a nonempty recovered journal was applied there.

The orchestrator must keep public writers closed during reapply and invalidation. These receipts prove committed owning database effects at that authority, not ingress isolation or external replication. ACCOUNT and STUDY_SERVER terminal authority must close access before cleanup; RESOURCE cleanup reuses the existing terminal ingestion fences. #332 must verify all seven required participant receipts at the pinned external watermark, both invalidation receipts, and each explicit pending/preserved disposition before opening the deployment.

Backups and their conditional expiry are separate from journal acknowledgement. No fixed deletion deadline, guaranteed recovery point, or successful external storage is implied.

Search now implements the same private reapply/receipt routes and ordinary durable terminal commands. RESOURCE removes the exact derived index rows and returns COMPLETE; both durable upserts and legacy index replacement share the terminal transaction lock and discard stale recreation. STUDY_SERVER removes directly scoped rows and blocks their recreation, but remains PENDING because legacy course-only rows require canonical source reconciliation. ACCOUNT cancels retained source exports and remains PENDING for source-authored index deletion. These receipts do not imply the missing canonical reconciliation is complete. Private HTTP credential, rollback, replay, replacement and overlapping-writer tests exercise the actual search repository and migrations.
