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
| `/discard` | `inventoryId` | Local snapshot removed, maintenance still active |

Identities are nonzero UUIDs. Authority is the exact applied `{revision,digest}`.
The backup identifier is a requested linkage, not proof that any database archive
exists or corresponds to this catalogue. The enclosing backup orchestration must
verify that relationship. No endpoint releases maintenance, writes object bytes
or declares provider quiescence.

Capture takes the source terminal lock before the existing budget lock. It
requires the exact applied prefix and rejects individual terminal deliveries
beyond that prefix, source deletion requests lacking canonical authority, unknown
storage namespace, unsettled physical mutations, unsettled source writes, any
remaining worker lease and legacy references. Every deleted Study Server requires
matching READY current and restore-derived COURSE and CHANNEL scope in recovery
mode. Resource rows with no Study Server ID still match through their course.

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
kind, immutable key, byte size, SHA-256, source resource state, `sourceRetained`,
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
commits one physical mutation before dispatch. Terminal, absent, rejected or
changed references cannot authorize a write. The adapter clones at most 10 MiB,
verifies size and SHA-256 against that source tuple, and uses local CREATE_NEW or
S3 If-None-Match. Uncertain completion keeps the operation outstanding. This path
has no HTTP endpoint yet, does not release maintenance, and does not itself
establish encrypted archive provenance or an externally quiescent writer.

The current tests establish query, transaction and disabled-API contracts using
explicit synthetic rows. They do not establish the real canonical #251 replay.
Private spool disposition, original-writer and provider closure, exact encrypted
object capture/read-back, database linkage, create-only restoration and full
native PostgreSQL/source effects remain required before activation. An empty
recovery tmpfs proves none of those conditions.
