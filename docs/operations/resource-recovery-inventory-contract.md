# Source-owned resource inventory

This is the disabled #342 metadata contract. It requires accepted #251 media V5
and #342 V6. Both `chanter.recovery-mode=true` and
`chanter.media.recovery-inventory-enabled=true` are required to construct the
service and controller. Production composition sets neither inventory activation
nor an alternate cutover path. The global recovery capability remains OFF.

The private base is `/api/v1/internal/resource-recovery/inventory`. Each POST
requires the owning media internal token. JSON is limited to 4 KiB before parsing;
duplicate, unknown, missing and trailing fields are rejected. Responses use
`Cache-Control: no-store`. Private keys and identifiers belong only in the bounded
protocol, never operational logs.

| Suffix | Exact body | Result |
| --- | --- | --- |
| `/fence` | `inventoryId` | Active maintenance identity, start time, nullable namespace SHA-256, outstanding local mutation count |
| `/capture` | `inventoryId`, `databaseBackupId`, `authority` | Immutable source catalogue snapshot header |
| `/page` | `inventoryId`, `authority`, `after`, `limit` | Header plus ordered reference page; limit 1–256, after is the previous ordinal or zero |
| `/discard` | `inventoryId` | Local snapshot removed only with no outstanding mutations; maintenance still active |

Identities are nonzero UUIDs. Authority is the exact applied `{revision,digest}`.
The backup identifier is a requested linkage, not proof that any database archive
exists or corresponds to this catalogue. The enclosing backup orchestration must
verify that relationship. No endpoint releases maintenance, writes object bytes
or declares provider quiescence.

Capture takes the source terminal lock before the existing budget lock. It
requires the exact applied prefix and rejects individual terminal deliveries
beyond that prefix, source deletion requests without an applied permanent RESOURCE
target for the same resource, unknown
storage namespace, unsettled physical mutations, unsettled source writes, any
remaining worker lease and legacy references. Every deleted Study Server requires
matching READY current and restore-derived COURSE and CHANNEL scope in recovery
mode. Resource rows with no Study Server ID still match through their course.
For every terminal server, the derived basis must also match the current verified
`chanter.recovery-restore-id` and each original archived scope digest. Missing
runtime identity, copied scope from an earlier restore or changed archive content
refuses capture, paging and maintenance PUT. This repeats the owning #251 basis
contract using a bounded streamed check under the same locks.

The source request's `event_id` identifies the request outbox event. It is not the
canonical journal event ID. The permanent target comes from #251's validated
canonical import/delivery and must be within the exact applied prefix. This check
does not prove normal job acknowledgement or physical deletion completion.

One temporary snapshot exists inside media at a time. It contains at most 250,000
references and is created atomically using bounded batches. It includes the
current key and each distinct retained migration key. Invalid key ownership,
hash, size, state, duplicate key or count aborts the whole transaction. The header
binds inventory/backup identity, authority, namespace, capture time, reference
count and an ordered SHA-256 chain. Historical copies belong in the existing
encrypted archive manifest; this temporary projection is not new ownership
authority. Discard removes only this projection and never clears outstanding
operations or the physical maintenance fence.

Reference fields are ordinal, resource ID, source course ID, CURRENT/MIGRATION
kind, storage backend, immutable key, byte size, SHA-256, source resource state, `sourceRetained`,
`terminal` and `providerVersionId`. Provider version remains null. `sourceRetained`
copies the source byte reservation; it does not claim that a provider object
exists. `terminal` includes direct account/resource/server fences and verified
current/historical course scope. It prohibits restoring a deleted target even
while physical cleanup remains pending. Live source authorization and original
quarantine state must still govern all later access.

Page reads require the same active maintenance identity, namespace and exact
current prefix. A later prefix, changed namespace, new unknown operation or
unresolved source state refuses the read. The immutable snapshot and deterministic
cursor allow bounded restart without replacing or widening the original set.

The internal adapter maintenance PUT path keeps the same global fence active.
Source authorization verifies snapshot, backup identity, applied prefix, namespace
and the exact current resource tuple under terminal-before-budget locks, then
validates size and SHA-256, then commits one physical mutation before dispatch.
The captured backend is included in the reference digest and must match both the
current source row and the configured adapter. A backend change requires a new
qualified inventory; an old snapshot cannot authorize a different backend.
Terminal, absent, rejected or changed references cannot authorize a write. The
adapter supplies a private copy of at most 10 MiB and uses local CREATE_NEW or
S3 If-None-Match. Uncertain completion keeps the operation outstanding. This path
is exposed only through the disabled private adapter below, does not release maintenance, and does not itself
establish encrypted archive provenance or an externally quiescent writer.

The internal maintenance DELETE path uses that same active fence and exact
inventory/backup/prefix/backend identity. Only a captured terminal reference whose
current source row is settled and DELETE_PENDING or DELETED may reserve the
operation. Every current and distinct migration key has its own closure record
inside the existing temporary snapshot. A successful local deletion or definitive
remote absence commits that receipt and removes its exact active DELETE mutation
in one transaction. Repeated closed references return without deleting again.
The receipt is mutable progress and is separate from the immutable reference hash.

Rejected DELETE calls can settle their invocation without writing a closure
receipt. Ambiguous provider completion or failed receipt commit retains the
outstanding operation, blocks retry and cannot release source quota. Both ordinary S3 deletion and recovery
refuse Enabled or Suspended versioning: a versionless request cannot erase
retained historical versions. The provider must implement the bucket-versioning
contract, and external configuration/writer fencing remains independently required.
The loopback tests establish adapter behavior only, not a real provider policy.
The provider assumptions follow the documented S3
[versioning response](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetBucketVersioning.html)
and [delete behavior](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html).
A delete response carrying a version ID, a true delete marker or a malformed
marker value also retains uncertainty, including an HTTP 404 response. An explicit
literal `false` marker value means no delete marker and does not prevent closure.
Ordinary deletion checks versioning after reserving
its mutation but before dispatching DELETE. A refused or unavailable versioning
query settles that unstarted invocation and cannot complete source deletion.
Both versioning and DELETE consume the existing cleanup request budget. Missing
bucket-versioning permission or an unsupported provider API therefore leaves
deletion pending; it cannot be treated as proof that versioning is disabled.

Source metadata/quota completion still requires the accepted #251 owning
`finishVerifiedMaintenanceDelete` hook in the same source transaction after all
retained keys have exact closure receipts. `requireClosedDeletionLocked` rejects
calls outside that transaction and rechecks every current/distinct migration key
before returning the exact owning tuple. The hosted preview invokes the actual
source hook in that transaction; its new runtime proof is pending. The private
adapter cannot add an absence flag, fabricate a worker lease or release maintenance.

The current tests establish query, transaction and disabled-API contracts using
explicit synthetic rows. They do not establish the real canonical #251 replay.
Private spool disposition, original-writer and provider closure, exact encrypted
object capture/read-back, database linkage, create-only restoration and full
native PostgreSQL/source effects remain required before activation. An empty
recovery tmpfs proves none of those conditions.

## Disabled production object adapter

This #342 checkpoint remains in this worktree and PR #343. It adds the actual
private transport needed by the existing encrypted object/manifest operator,
using the pinned #251 source tuple and completion contract. Both existing recovery
flags are required. Every route uses the owning internal token, no-store responses
and fixed paths under `/api/v1/internal/resource-recovery/objects`.

`read`, `put` and `delete` select only the existing RestoreRequest fields:
inventoryId, databaseBackupId, authority and ordinal. No caller supplies a key,
backend, URL or filesystem path. READ resolves a currently restorable reference
under the terminal/budget transaction, reads at most 10 MiB outside that transaction,
verifies exact size/SHA-256, then resolves and compares the same reference again
before returning bytes. Terminal, changed, unknown-writer or wrong-namespace state
refuses disclosure. This private backup authority includes retained QUARANTINED
and SCAN_FAILED bytes, without making either state publicly available.

PUT sends a four-byte big-endian metadata length, strict UTF-8 JSON metadata of
at most 4 KiB, then at most 10 MiB of raw bytes. The existing source authorization
and create-only adapter validate bytes before reserving the physical mutation.
DELETE sends strict JSON and uses the existing per-reference closure accounting.
Neither operation exposes maintenance release. Every success receipt repeats the
exact request and operation; helper execution success alone is insufficient.

The fixed localhost helper takes operation names only. Metadata/bytes stay on
bounded stdin/stdout, the token stays in the owning service environment, redirects
and proxy routing remain disabled, and errors never include body/key/token values.
The host adapter verifies receipts and enters returned bytes directly into the
existing encrypted object archive. The enclosing restore operator must validate
the full manifest before calling PUT; the transport alone is not archive authority.

Final source quota/metadata completion requires a source-supplied implementation
of the narrow completion binding. It runs the real #251 MANDATORY hook in the
same transaction as `requireClosedDeletionLocked`; no binding means refusal.
The `/finish-delete` body contains only inventoryId, databaseBackupId, authority
and resourceId. The source resolves every current/distinct migration reference.
The binding is not a new authority store or caller-supplied physical proof.
Before accepted #251 integration, tests can prove this transaction boundary but
cannot claim production completion. These adapters establish neither external
writer quiescence nor retained-provider-version erasure, and capability stays OFF.

Focused tests cover denied/changed source reads, corrupt and oversized bytes,
strict authenticated framing, the absent completion binding and owning transaction
rollback. The fixed helper transfers the full 10 MiB bound with a 64 MiB heap;
metadata-only calls retain 32 MiB. No service container limit is increased. The
capture adapter feeds actual source pages and raw reads into the existing restic
manifest flow, whose real local encrypted roundtrip passes. Source HTTP integration,
combined container memory and the actual #251 completion binding still require
native dependency proof. No operator command or capability is activated here.
