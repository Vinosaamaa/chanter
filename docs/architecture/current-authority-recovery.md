# Current deletion authority during recovery

Issue #332 extends accepted database/configuration recovery without replacing
pgBackRest or restic. This implementation starts from accepted #330 and consumes
#251's private terminal-journal contract. The independent lane owns deployment
replication and recovery orchestration. Auth and the other services retain their
canonical journal, terminal fences, cleanup and session invalidation.

## Acceptance and limits

The first deliverable proves encrypted journal persistence, complete-prefix
validation, checkpoint ordering and isolated recovery against owned storage and
PostgreSQL fixtures. It does not configure a provider, publish a deployment,
promise free quotas or declare application recovery complete. The actual provider
drill, object inventory/version checks, source consistency, reviewed retention,
operator notification and public cutover remain explicit gates in #332 and #252.

There is one issue branch and one linked pull request. The existing #330 tree and
all other worktrees remain untouched. No local browser, preview or server is
started. Native Windows tests use local storage and injected service clients;
real service/database integration runs in the existing hosted native workflow.

## Trust and current authority

The hash chain detects corruption, missing entries and forks. It is not a
signature and cannot establish freshness against a repository administrator who
rolls the entire repository back. Recovery trusts the independently administered
encrypted repository, verified TLS, its protection policy and the operator's
current minimum watermark. A checkpoint restored from the old database is only
a lower bound. It never selects the current external journal.

Production replication requires the configured remote backup origin and a
separate terminal-journal repository prefix and encryption password. Passwords,
credentials and journal bodies do not appear in command arguments or output.
The encryption recovery key must be held outside both the source host and the
repository it encrypts. The fixture repository uses the same pinned restic
binary and encrypted format, but a fixture write cannot acknowledge a real auth
checkpoint or qualify as independent off-host evidence.

A recovery retains the exact externally selected prefix for its whole operation.
Before completing the authority stage, it reads the external head again. A
changed or unavailable head requires another isolated reconciliation. This check
alone is not an atomic public cutover: the original deployment's writers must be
fenced through the operator's cutover procedure. There is no automatic ingress
opening or invented distributed lease in this slice.

## Replica format and publication

Reuse checksum-pinned restic 0.19.1 from the accepted release bundle. Do not add
custom encryption, a second backup format or a retry service. The existing host
operation lock and scheduler own serialization and later retries. Each invocation
has bounded subprocess deadlines and fails with a content-free error.

The service protocol is schema 2 from `terminal-journal-contract.md` in #251.
Validate each page before persistence: exact supported fields, nonzero target
UUID, fixed kind/action, safe positive integer revision, canonical millisecond
Java timestamp, previous digest, recomputed SHA-256 digest, contiguous revisions,
pinned upper watermark and matching final watermark. Reject empty nonterminal
pages, changed bounds and duplicate/conflicting target authority. The JSON
implementation must reject revisions beyond its exact integer range.

Every entry requires `retentionPolicy: PRESERVE_MODERATION_RECORDS_V1`, included
after the deletion timestamp in the version 2 digest text. Missing or unknown
policy and version 1 pages are rejected. This fixed scope preserves previously
collected moderation snapshots, notes and audit records. It does not preserve
original resource bytes or infer a general legal hold. The protocol is unreleased;
there is no production version 1 import or migration claim.

Store bounded immutable page objects as encrypted restic stdin snapshots. A
manifest records its schema, environment, checkpoint identity, source watermark,
creation time and ordered page references with their content digests and
watermark ranges. Page references use full immutable snapshot IDs. Snapshot tags
contain only fixed protocol/environment identifiers and bounded watermarks; no
target IDs, source content or secrets belong in operator output.

Read back every new page and the manifest through restic, validate the complete
referenced chain from genesis, and compare the final prefix before posting a
checkpoint. Restic command success alone is insufficient. The storage reader uses
bounded page buffers and a bounded manifest/listing, rejecting capacity overflow
instead of truncating authority. Previously sealed full pages can be reused;
the last partial page can be replaced in a newer manifest without changing an
older immutable snapshot. Retain superseded snapshots until an explicit reviewed
pruning policy proves that all required prefixes remain recoverable.

Select the greatest valid revision from the repository's checkpoint manifests,
not merely the most recent wall-clock timestamp. Conflicting digests at a
revision, a chain that does not extend the required prefix, an unavailable page
or an unverifiable manifest fails closed. No caller may silently select an older
manifest after failure. Deterministic checkpoint identity for the same environment
and prefix makes a retry after a lost auth acknowledgement idempotent. A newer
auth acknowledgement must be reconciled with the repository rather than reduced.

Only after full external read-back verification may the replication client post
`{revision,digest,checkpointId}` to auth. Read and validate the returned checkpoint.
If the network response is lost, the next invocation verifies storage again and
repeats the same identity. Unfinished page writes do not become a checkpoint.
The empty journal still requires a verified genesis manifest before an empty
checkpoint can be acknowledged.

The initial implementation caps one prefix at 250,000 entries, 500 stored pages,
256 KiB per page, a 1 MiB manifest and 20,000 listed manifests. It verifies a
candidate's complete stored pages before publishing the manifest, then reads the
published manifest and pages again before acknowledgement. Duplicate targets
across pages therefore cannot publish a corrupt newest manifest. A run has a
15-minute budget checked between bounded operations. Restic processes have a
60-second deadline and 256 MiB Go memory target. Exhausting a bound requires an
explicit capacity decision; it never truncates or acknowledges incomplete work.

The production host cannot reach unpublished container ports directly. A small
JDK helper runs through Docker Compose exec inside the owning service and calls
only fixed lifecycle routes at `127.0.0.1:8080`. It takes bounded JSON on stdin,
reads the internal token from that service's environment, disables HTTP proxying
and redirects, and writes only successful bounded response bodies to stdout.
A 20-second whole-process deadline covers stalled stdin and streaming responses;
the host also imposes a 30-second execution deadline. Container exit success
is transport evidence only. The orchestrator separately verifies every receipt.

## Isolated recovery stages

1. Use the existing `restore-isolated.mjs` result. Require its owned new volume,
   immutable release and matching configuration evidence. The database remains
   unexposed; external delivery and ordinary writers remain disabled.
2. Load the current independent journal, verify the complete chain, and require
   it to cover the operator's minimum watermark and any restored checkpoint.
   Missing authority is not an empty journal.
3. Apply bounded pages through each participant's private
   `POST /api/v1/internal/lifecycle/journal/reapply`. Required sources are auth,
   community, message, media, agent, notification and search. Auth imports the
   original canonical entries before its participant mutations in the same
   transaction. No source can substitute a local cleanup count for applied
   prefix authority.
4. Read `/api/v1/internal/lifecycle/journal/reapply/receipt` from every source.
   Require schema 1, exact source, exact revision/digest, nonnegative safe counts
   and explicit pending/preserved cleanup disposition. A failed or omitted
   participant keeps recovery isolated. Retained payloads do not undo terminal
   access fences and are never labelled physically erased.
5. Call auth and agent
   `POST /api/v1/internal/lifecycle/recovery/invalidate-sessions` with the stable
   recovery UUID and exact authority. Verify the durable matching receipt and
   scopes `ALL_BROWSER_SESSIONS` and `ALL_PENDING_NATIVE_REQUESTS`. Auth also
   verifies its canonical head. Repeated calls must remain idempotent.
6. Recheck external current authority and every required participant receipt.
   Persist only a bounded safe recovery receipt. A completed authority stage
   still leaves `publicCutoverAllowed: false` until independent application,
   object, writer-fencing and operator gates are implemented and verified.

Email, AI generation, ordinary indexing fanout and public media remain disabled
during these stages. Necessary terminal cleanup is source-owned recovery work,
not permission to resume ordinary event dispatch. The #251 handlers must expose
that distinction. This orchestrator does not manufacture their effects.

## Media-owned object recovery contract

This is the required next contract, not an implemented inventory or permission
to restore objects. Media owns resource identity and lifecycle. Its current
`course_resources` rows hold `storage_key`, `byte_size` and `sha256`, but no
provider version ID. Account export and bucket listing cannot replace this source
inventory. Account export has viewer scope; bucket listing cannot establish which
resource owns an object or whether current deletion authority forbids it.

The existing S3 adapter creates keys under
`resources/v1/<course>/<resource>/<random-object>` with `If-None-Match: *`.
Ordinary available content therefore has a stable key, size and digest. This
does not prove that a provider administrator cannot overwrite bytes. Legacy
migration allocates a different key and changes the source row; an active or
uncertain migration is not a stable recovery checkpoint. The adapter does not
capture a provider version ID. The inventory must return that field as explicit
`null`, meaning unknown, rather than substituting an ETag, timestamp or digest.

Media must expose a private, source-owned inventory with these semantics:

- Bind every page to one inventory identity, exact schema/release and database
  recovery point, storage namespace fingerprint and applied journal authority.
  The database backup must reference the completed encrypted inventory manifest.
- Read canonical rows with stable keyset pagination. Return at most 500 entries
  and 256 KiB per page, with an explicit continuation and terminal page. An
  initial 250,000-entry limit and bounded operation deadline reject overflow;
  the existing byte budget alone does not bound the number of tiny resources.
- Each extant-object entry names its resource and course, storage backend/key,
  exact byte size, lowercase SHA-256, nullable provider version ID, original
  lifecycle state, `storage_write_settled` and explicit recovery disposition.
  A terminal entry identifies its source fence and journal authority; historical
  object fields are included only when the source still knows them. Never invent
  a key or hash for a deleted row. Keep these identifiers inside the encrypted
  archive and authenticated private protocol, out of operator logs.
- Distinguish extant bytes, terminally deleted content and unresolved writes.
  Include private and quarantined resources that still own bytes. Public
  `AVAILABLE` filtering would silently lose private retained content. A source
  row that lacks a stable hash/key or has an uncertain dispatched PUT must fail
  qualification instead of disappearing from the inventory.
- Terminal fences remain canonical source state even when an old resource row
  is gone. Apply current RESOURCE, ACCOUNT and STUDY_SERVER authority through
  the owning participants before deciding which historical objects may return.
  Unresolved ownership reconciliation blocks the affected recovery. A deleted
  object is never eligible merely because a backup still contains its bytes.

The first supported capture should use a verified maintenance checkpoint.
The existing deployment operation lock serializes the operator command, but it
does not stop application writers. Stop ingress and ordinary workers, settle
all previously dispatched storage writes, and verify the source remains fenced
before exporting inventory and taking the linked database backup. An uncertain
  provider outcome prevents the checkpoint, even after the API process stops.
  `storage_write_settled` must be true; stopping a process does not prove a
  dispatched remote write has ended. Capture and verify required object
bytes while that state remains stable, then publish the backup linkage only
after every page and object passes read-back. Define and test the exact bounded
source operation with #251 before enabling it. This choice trades maintenance
time for a small, testable first contract; no such command is enabled yet.

An online database backup remains database-only evidence until it has a proven
object-coverage protocol. Capturing inventory before the backup can omit new
uploads; capturing afterward can omit rows or bytes deleted during the backup.
A read transaction alone cannot make remote object writes atomic. Do not add a
second ingestion retry loop or assume a list performed at a convenient time
solves this. Continuous recovery points would require separate proof that every
resource reachable at the selected database point has an archived exact copy or
a current authoritative terminal disposition.

Use the already pinned restic implementation for encrypted object copies in a
separate configured resource-backup prefix. Stream one object at a time with an
exact size bound, recompute SHA-256 over the complete bytes, and bind its immutable
restic snapshot reference to the inventory entry. The current upload ceiling is
10 MiB per object; a provider that streams extra data must be rejected at that
bound. Hash the complete decrypted read-back before publishing the manifest.
ETags, object metadata, successful PUT responses and restic exit status are
insufficient by themselves. This manifest records backup evidence; it does not
become a parallel resource catalogue or grant access.

During recovery, replay current authority first and obtain a fresh source-owned
inventory from the isolated restored database. Every eligible extant object must
match a retained backup copy by namespace/key, size and digest. Missing copies,
changed bytes, unexpected duplicate keys or unresolved source dispositions keep
recovery closed. Restore only those eligible objects into the reviewed private
destination with create-only writes. If a key already exists, verify its full
bytes and reject a mismatch; never overwrite it to make the check pass. Re-read
the restored bytes, recheck source inventory and current authority, and preserve
the original availability/quarantine state and ordinary live authorization.
Changing storage namespace needs an explicit source-owned migration because the
current media budget binds its namespace. No destination remapping is implicit.

Current deletion authority overrides every historical copy and checkpoint.
`PRESERVE_MODERATION_RECORDS_V1` preserves collected moderation evidence, notes
and audit, not original resource objects. Retained encrypted backup bytes remain
subject to the reviewed backup deletion window; they must not be described as
physically erased while retained. Pruning must preserve copies required by each
retained, qualified database point without making deleted content restorable to
users. Its retention policy and independent journal protection are release gates.

Immutable keys plus verified encrypted copies can provide exact-byte recovery
without paid provider versioning. This is a technical property of the proposed
protocol, not a verified free-provider capacity claim. The existing 8,000,000,000
byte live-storage cap does not include retained deleted/replaced copies, database
chains, WAL, configuration, journal history or repository overhead. Many tiny
objects also consume request budget. Measure all retained unique bytes, request
and egress use, recovery host space and deadlines against the actual independent
provider allocation before enabling capture. Provider accounts, retention and
capacity are still unconfigured; no free quota or protection promise is assumed.

Required fixture proof includes a private available object, a quarantined object,
a missing copy, a corrupted copy, a mismatched existing destination, an unknown
version ID, concurrent upload/delete attempts during capture and a delayed PUT
whose outcome is unknown. Restore an older database plus a newer RESOURCE and
parent-scope deletion prefix and prove deleted bytes never become eligible.
Repeat capture/read-back through actual encrypted local storage and the owning
PostgreSQL transactions. Provider retention and original-writer fencing still
require independent operational proof after those fixtures pass.

## Failure and retention rules

Keep failed recovery state and its owned volumes for inspection. Stop only
resources proven to belong to the attempt. Do not overwrite a deployment, delete
a prior recovery, clear terminal markers, revive sessions or reverse migrations.
Do not store tokens or target lists in the operator receipt.

No journal pruning is enabled initially. A later pruning operation must prove
that a complete current prefix and every retained database backup's required
authority remain recoverable, including the separately encrypted configuration.
Object retention must cover the database recovery window without contradicting
the deletion policy. Capacity exhaustion fails replication and leaves deletion
pending; it must not acknowledge an incomplete external prefix.

## Verification

Test the Java-compatible canonical digest against fixed #251 vectors. Cover
corruption, gaps, duplicates, changed upper bounds, missing pages, stale/forked
manifests, empty genesis, wrong encryption keys, partial storage writes and lost
checkpoint responses. Assert that every failure before verified persistence
leaves the checkpoint client untouched. Use an actual encrypted restic fixture
and prove plaintext canaries are absent from its stored files.

Recovery tests inject each participant failure and stale receipt, all-session
invalidation mismatch, canonical auth divergence and an external head advancing
during replay. Preserve idempotency across process restart. Hosted PostgreSQL
must repeat the real #251 source transactions once those routes are accepted;
fake receipts never stand in for that proof. Both native architectures repeat
the storage and database recovery tests. Provider quotas and actual off-host
freshness/protection remain unverified until a separate provider drill succeeds.

## Operator integration checkpoint

`host.mjs prepare-recovery STATE` adds a separate
`CHANTER_TERMINAL_JOURNAL_PASSWORD` without rotating existing keys. Preserve it
in the offline recovery key store. `host.mjs init-terminal-journal BUNDLE STATE`
initializes the distinct encrypted repository. After #251 is accepted and the
matching release is deployed, `host.mjs replicate-terminal-journal STATE`
requires the accepted release, configuration fingerprint and existing migration
floor, then replicates through auth's private route under the deployment lock.
It records only status, release and bounded authority metadata. Errors omit
provider bodies, tokens, target IDs and local paths.

This checkpoint has no automatic journal timer or public cutover command. The
existing backup scheduler will own recurring
replication after the actual source routes and bounded provider behavior are
verified. Injected recovery clients exercise sequencing only. #251's real seven
participant effects and both durable invalidations, #249 authority rules, matching
isolated runtime startup, object checks, provider freshness protection and
original-writer fencing remain required before operational application recovery.

The isolated composition also requires an explicit matching release capability,
`recoveryProtocol: {journalSchema:2,ordinaryWorkIsolation:1}`. Build policy remains
disabled until the accepted source union and native isolation proof are complete.
Historical bundles without it are rejected before starting any application. No
migration or silent binary upgrade occurs during recovery. The composition starts
only the seven required sources and restored PostgreSQL on an internal Docker
network, with no published ports, source HTTP bound to container loopback,
archiving disabled and no media/scanner volumes. `CHANTER_RECOVERY_MODE=true`
omits ordinary worker beans while retaining private replay and invalidation.
Media receives two empty, private 16 MiB scratch filesystems at its existing
legacy-resource and upload-spool paths because its constructors require them.
They contain no restored objects and cannot establish absence or physical erasure.
Production retains its configured S3 client; construction makes no provider call.
Normal email, outbox, ingestion and media flags are also disabled explicitly.
External error reporting is disabled and all telemetry exporters are `none`.

The Linux-only `restore-current-authority.mjs` command accepts the matching
bundle, private bootstrap settings, existing isolated restore directory,
environment and operator minimum-authority JSON file. It rejects missing release
capability before configuration decryption or Docker operations. It decrypts the
exact backup-bound configuration, checks source image identity and verifies the
old database container and volume labels, mounts, absent published ports and
detached networking before stopping that database. It uses the same restored
volume on the new internal network; it does not migrate or create a replacement
database. Only containers with the exact recovery/project/service labels and
expected images, mounts and networks can be stopped by the command.

Recovery requires exclusive operation on a trusted Docker host. The original
database is stopped by its inspected immutable container ID and is never removed;
its volume reference prevents ordinary volume removal. A changed container name
cannot select a replacement. Post-start image, mount and network checks run before
replay and again before the receipt. These checks do not defend against a malicious
Docker-privileged actor or establish that an original writer on another host is
fenced. That independent writer-fencing requirement remains a cutover blocker.

Before any source starts, a fixed one-shot helper validates every packaged Flyway
migration against the restored database. Its connections are read-only, queries
and process lifetime are bounded, and missing, changed or unexpected migration
state rejects recovery. It never calls migration or repair. The native fixture
checks all source schemas and deliberately changes one checksum to prove refusal
before startup, then restores that fixture checksum and verifies it again.

Each selected external prefix owns a private attempt directory and durable
recovery UUID. Retries of the same prefix reuse that identity; a newer external
prefix gets a distinct attempt and never overwrites the old one. Configuration
files stay private. Both success and failure stop only verified owned application
containers and PostgreSQL, preserving all volumes and attempt state. The latest
attempt status governs completion; an older receipt never overrides a failed
retry. The returned authority-stage receipt still denies public cutover. Actual
runtime/participant proof is pending and the build capability remains disabled.

References: [restic backup and stdin behavior](https://restic.readthedocs.io/en/stable/040_backup.html),
[repository snapshot operations](https://restic.readthedocs.io/en/stable/045_working_with_repos.html),
and [accepted backup operations](../operations/backup-and-recovery.md).
