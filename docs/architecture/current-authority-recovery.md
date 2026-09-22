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

The service protocol is schema 1 from `terminal-journal-contract.md` in #251.
Validate each page before persistence: exact supported fields, nonzero target
UUID, fixed kind/action, safe positive integer revision, canonical millisecond
Java timestamp, previous digest, recomputed SHA-256 digest, contiguous revisions,
pinned upper watermark and matching final watermark. Reject empty nonterminal
pages, changed bounds and duplicate/conflicting target authority. The JSON
implementation must reject revisions beyond its exact integer range.

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

This checkpoint has no automatic journal timer, isolated application startup or
public recovery command. The existing backup scheduler will own recurring
replication after the actual source routes and bounded provider behavior are
verified. Injected recovery clients exercise sequencing only. #251's real seven
participant effects and both durable invalidations, #249 authority rules, matching
isolated runtime startup, object checks, provider freshness protection and
original-writer fencing remain required before operational application recovery.

References: [restic backup and stdin behavior](https://restic.readthedocs.io/en/stable/040_backup.html),
[repository snapshot operations](https://restic.readthedocs.io/en/stable/045_working_with_repos.html),
and [accepted backup operations](../operations/backup-and-recovery.md).
