# Terminal recovery journal

Owning issue: #251. Deployment replication and restore gating: #252. This is a private service protocol. Do not expose these routes through the public gateway.

Auth migration V8 stores the terminal journal and checkpoint. The migration must follow accepted #249 auth V5–V7; no deployment may run the intermediate branch with those migrations missing and later depend on Flyway out-of-order execution.

## Export and checkpoint

All routes use `X-Internal-Service-Token` with the auth service's configured internal credential. Responses have `Cache-Control: no-store`. The credential is not a browser token, export download credential, or proof that external storage succeeded.

`GET /api/v1/internal/lifecycle/journal?after=0&limit=100` returns schema version 1, `after`, `through`, `entries`, and `next`. Each watermark is `{revision,digest}`. The first request pins the latest committed `through`. Subsequent requests pass `after=<previous next.revision>&through=<pinned through.revision>`. Page size is 1–500. Stop only when `next` exactly equals `through`. An empty journal has revision 0 and a digest of 64 zeroes.

Each entry contains `revision`, `eventId`, `targetKind`, `targetId`, `action`, `deletedAt`, `previousDigest`, and `digest`. Target kind is ACCOUNT, STUDY_SERVER, or RESOURCE. Action is DELETE. UUIDs identify targets; no email, profile, content, session token, evidence, or storage credential is included. A target appears once. Allocation and the owning terminal mutation share a database transaction and a serialized head lock. Rollback cannot create a missing revision or publish later authority before an earlier transaction commits.

The digest is lowercase hexadecimal SHA-256 of UTF-8 text, with one newline after every field, including the last:

```text
1
<revision as decimal>
<eventId UUID>
<targetKind>
<targetId UUID>
DELETE
<deletedAt as Java Instant.toString(), millisecond precision or coarser>
<previousDigest>
```

Verify schema, fixed target/action values, UUIDs, contiguous revisions, previous digest, every entry digest, pinned upper watermark, and the final digest. `TerminalJournal.Page.validate()` implements the same checks for Java participants. A digest is an integrity chain, not an external signature or storage attestation.

After writing and verifying the entire prefix in independently recoverable external storage, #252 posts `POST /api/v1/internal/lifecycle/journal/checkpoint` with `{revision,digest,checkpointId}`. `checkpointId` is an opaque UUID assigned to that durable checkpoint. Auth rejects a revision beyond its committed head, a mismatched digest, or a different identity for the same latest checkpoint. Identical retries succeed. Older acknowledgements return the newer recorded checkpoint. `GET /api/v1/internal/lifecycle/journal/checkpoint` returns `{checkpoint:null}` until the first acknowledgement.

Auth records the trusted recovery caller's acknowledgement. The caller must not acknowledge a local-only file, incomplete object, unverified write, or storage that would disappear with the source host. Deletion remains pending until this acknowledgement covers its terminal revision. No provider or external replication proof is supplied by the application test suite.

## Restore integration

Recovery must obtain the current independently stored journal and verify its chain. A checkpoint remembered in a restored database does not prove it is the current external checkpoint. Missing, discontinuous, older-than-required, or unverifiable authority keeps the deployment isolated and nonpublic. Replaying a database backup alone is insufficient.

The participant reapply endpoint and per-service receipts are the next #251 implementation step. Do not claim that journal export currently purges restored content. Restoring must invalidate all browser sessions and pending native requests. ACCOUNT and STUDY_SERVER terminal authority must close access before cleanup; RESOURCE cleanup reuses the existing terminal ingestion fences. A participant receipt must commit with its terminal mutation and reject an inconsistent replay. #252 must wait for every required receipt at the pinned external watermark before opening the deployment.

Backups and their conditional expiry are separate from journal acknowledgement. No fixed deletion deadline, guaranteed recovery point, or successful external storage is implied.
