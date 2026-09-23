# Issue #342 review dispositions

Full CodeAnt review completed for `96d807c9a220286b7a2fc6a4d9bcef3d88cc0b6d`.
This draft remains blocked on the accepted #251 source union and full recovery
proof. The recovery capability stays OFF. Passing the current tests does not
remove those dependencies.

## Findings

The full review at `1e38bdbf` raised four further observations:

- `4078153166`, backend identity: confirmed and fixed. The behavioral regression
  first permitted restoration after the source backend changed. Inventory now
  captures and hashes the backend, and restoration requires agreement between
  that snapshot, the current source row and the configured adapter before any
  mutation is reserved. Changed-backend and wrong-adapter regressions pass.
- `4078153175`, derived scope replacement: the current source importer rejects a
  different basis or digest for an existing READY scope. Recovery has no supported
  reset that clears those tables while retaining inventory. The qualification
  check binds the immutable scope to this restore ID and original archive digest.
  A future owning reset would also have to discard inventory; none is introduced
  here. Independent read-only source review confirmed this boundary.
- `4078153182`, duplicate READY kinds: the owning V5 scope header primary key is
  `(study_server_id,scope_kind)`, with a COURSE/CHANNEL kind constraint. Two rows
  for one kind cannot satisfy the required two kinds in that schema. Synthetic
  schema tests do not replace the pending actual source migration proof.
- `4078153188`, local DELETE I/O failure: FINISHED reports synchronous invocation
  completion, not successful erasure. The adapter still throws typed DeleteFailure;
  the worker cannot finish deletion or release its byte reservation on that path.
  A maintenance physical-closure receipt must likewise require successful deletion,
  not merely a settled invocation. That separate closure path remains unfinished.

Full review at `7386051d` verified the backend fix. `4078237668` concerns the
pre-union upload caller: #251's source-owned CourseResourceService explicitly
unwraps a PutFailure whose cause is ResponseStatusException after accounting for
the typed outcome. The adapter preserves that cause. The source union is required;
do not copy an older caller or infer production readiness from this branch alone.

`4078239127` correctly warns that equal row counts alone cannot establish a stable
capture. The actual boundary also holds the terminal head and budget locks while
the SELECT streams its single statement snapshot. Source insertion, terminal
transition and approval changes take those locks. Qualification rejects staging,
scanning, legacy, unsettled writes and every lease; the remaining storage/worker
callbacks require those excluded states or an exact active lease. Maintenance
prevents a new claim or reservation. The final count is an additional consistency
check, not the concurrency mechanism. Actual source-union tests remain required;
synthetic query tests do not claim to prove every source writer.

- `4077774875`, local partial object: confirmed and fixed. A real failed copy
  regression first left a created object behind. The adapter now removes only
  its own successfully created file after closing the write stream. Failed cleanup
  retains UNKNOWN. The same regression verifies retry and preservation of an
  existing object. It passes with the affected inventory/adapter tests.
- `4077773572`, canonical authority wording: clarified. The query requires the
  trusted permanent RESOURCE target with the same resource ID within the exact
  applied prefix. #251 owns validation/import of that canonical target. The source
  request outbox event ID is different from the canonical journal event ID and
  must not be compared for equality. Job acknowledgement and physical erasure
  are separate conditions, not claimed by this query.
- `4077776784` and the missing-V5 suggestion in `5786511154`: confirmed integration
  dependency. #251 V5 creates `storage_write_settled` and the source lifecycle
  tables. The disabled bean fails closed without them. This PR must integrate
  accepted #251 before V6 migration or readiness; no duplicate V5 or out-of-order
  migration is introduced. Synthetic schema tests are not that integration proof.
- UNKNOWN PUT/migration suggestion in `5786511154`: confirmed source integration
  dependency. #251's typed write outcome and worker settlement path must be united
  with these adapters. An UNKNOWN operation cannot be cleared by retry or assumed
  complete because a worker lease ends. The accepted source union and actual
  delayed-write regression remain required before readiness.

## Custom suggestions

The subsequent full review at `ead6625e847c17cf366dc789a8e885b408816bcc`
confirmed the local residue fix and raised `4077832514`: malformed recovery bytes
could need a settlement write after their mutation had already been reserved.
The failed-settlement regression reproduced that path. Size/hash validation now
runs against the source tuple before inserting the mutation in the same source
transaction. Malformed bytes cannot leave a reservation, and no physical dispatch
occurs. Actual dispatched operations still retain uncertainty on lost settlement.

At `769d3dbb`, full review raised `4077906235` and `4077906240` about failed
settlement after local cleanup or successful S3 I/O. Keeping the durable record is
intentional; filesystem absence cannot replace an unconfirmed database commit.
The raw-exception reporting was improved: both adapters now return typed UNKNOWN
when completion accounting cannot commit, with the ACTIVE record retained. The
provider regression first failed against the raw exception; local failed-copy
cleanup and real remote completion are covered. No retry clears that uncertainty.

The same review's missing `CanonicalLifecycleFixture.java` suggestion refers to
the ordinary branch before its required source dependency. The explicit preview
merges the pinned #251 commit containing that exact helper before compiling it.
Both architectures already passed that union step. Ordinary release runs never
invoke the helper. This is still a preview, not a claim that #251 was accepted.

Review `4077906468` correctly found that ordinary auth/community composition has
no volume array. The fixture now initializes each service's volume list before
adding its read-only helper mount. Inspection also found that moving the compose
file into a private child directory would change relative environment and bind
paths; those existing paths are resolved against the original compose directory.
No credential values are read into the generated compose definition.

Full review completed at `b519bb27` and verified the missing-volume and typed S3
settlement fixes. Its remaining V5 observation is the same source-union gate.
The HTTP 429 suggestion conflates a finished invocation with a successful write:
an explicit rejection still throws `PutFailure` or `DeleteFailure`, and callers
cannot report success. FINISHED means that invocation returned a definitive
rejection; it does not claim the object exists or was erased. Transport failure,
408, 5xx and unconfirmed settlement retain UNKNOWN. Real provider closure remains
unproven. The merge-catch suggestion affects diagnostics only: missing MERGE_HEAD
or an unexpected conflict set aborts before committing or building.

The seventeen custom suggestions add no confirmed correctness defect. Retain the
private bounded byte copy and the second live source qualification around actual
read-back; neither can be replaced by cached snapshot identity. Raw command stderr
may contain private response content and is intentionally excluded. The hosted
fixture is already confined to its explicit non-publishing preview; no persistent
transport, artifact-sharing workflow or replacement retry framework is added.
The existing source anchors reject changed inputs. Prior dispositions cover the
remaining helper extraction and document layout suggestions.

Additional preview suggestions are dispositioned as follows: retain independent
native architecture builds rather than add artifact transfer; preserve canonical
text UUID ordering and the early capacity bound; retain bounded private byte
copies so verified bytes cannot change before dispatch; keep explicit disposable
helper calls and exact source-text anchors for the pinned temporary union. A
persistent helper/transport or new retry abstraction is not required. The ordinary
release smoke path calls the fixture only under the explicit preview environment
flag and never publishes that job. The four deferred participants and exact
conflict list are named in the preview documentation/script.

The sixteen custom suggestions in `5786511154` were assessed separately from the
findings. No automatic suppression was added.

- 1, 5 and 6 concern small helper extraction and adapter arguments. Current
  methods retain explicit source authorization and separate local/remote failure
  handling. A broader operation wrapper is unnecessary for this bounded change.
- 2 and 3 would move the inventory bound after work or skip live qualification
  during paging. Keep the early count before hashing/inserting and revalidate
  current authority, unknown operations and source state on every page. These
  checks intentionally refuse stale recovery work.
- 4 proposes combining bounded store queries or a denormalized counter. The
  singleton lock already serializes a table capped at 4,096 records. Keeping the
  count derived avoids another persistent invariant and reconciliation path.
- 7 proposes streaming ordinary-upload MD5. The existing upload validator caps
  files at 10 MiB; recovery also clones at most 10 MiB. This is a bounded existing
  allocation, not an unbounded input. No unrelated upload refactor is required.
- 8, 9, 12, 13 and 14 propose reusable fixtures. Independent databases isolate
  mutation/rollback tests, the small conditional contexts test real bean absence,
  and the short HTTP fixtures expose their exact dispatch/failure assertions.
  Their current setup is bounded and tears down owned resources.
- 11 proposes a new workflow abstraction for the two restart phases. Retain the
  explicit phase order and existing workflow; no second runner is needed.
- 10, 15 and 16 concern document layout. The design and contract already state
  dispatch, completion, uncertainty, restart and qualification separately. The
  implementation checkpoint and this disposition distinguish proven behavior
  from remaining source, provider and cutover requirements.
# Recovery PUT tuple concurrency

Full review at `912a067b` completed on September 23 at 01:57 UTC. Comment
4078334736 proposes a resource-row lock around the pre-dispatch tuple comparison.
The source-owner audit found no supported tuple-changing callback for a settled,
unleased AVAILABLE, QUARANTINED or SCAN_FAILED resource. The maintenance fence
blocks reserve and claim; migration completion requires its matching SCANNING
lease, which qualification excludes. Capture and PUT reservation already share
the terminal-head then budget locks with those source transitions.

A row lock released at the end of reservation would not cover later provider
I/O. Terminal deletion remains intentionally possible after that commit. It
invalidates the inventory while the durable physical mutation stays outstanding;
it cannot publish the terminal resource, qualify a new snapshot, or authorize
quota release. The completion caller separately rechecks the current prefix,
all retained keys and persisted physical closures while taking the resource lock
inside the owning deletion transaction. This suggestion is dispositioned against
those source semantics, not treated as proof of external writer closure. Future
tuple-changing callbacks must join this boundary. The owning source union and
hosted proof remain required.

The source owner subsequently added uploader unlinking after definitive physical
state and exact downstream acknowledgement. Inventory capture now also joins the
retained ACCOUNT-to-RESOURCE identity to its exact terminal target, revision,
event and digest. Both current and migration references stay terminal after the
uploader becomes null. The regression failed before the query change; all 35
inventory checks now pass, including seven mismatched identity cases. This uses
the tested source contract at `ec91670e`; it does not infer physical closure from
the retained identity or a missing uploader.

Full review at `f957d20d` completed on September 23 at 02:57 UTC. Comment
4078648000 correctly identified that discard could remove inventory ownership
while a physical operation was outstanding. The durable mutation itself survived,
but preserving its inventory reference is necessary for a late owning completion.
Discard and maintenance release now reject every ACTIVE or UNKNOWN operation
under the existing budget lock. Two regressions failed before the fix; all 49
inventory/mutation checks pass afterward.

Comment 4078647995 questions a PostgreSQL streaming cursor while a separate
statement flushes the captured references. A 513-reference regression now crosses
both flush boundaries and verifies complete unique pagination. It passes locally;
the existing native media workflow runs the same test against PostgreSQL on both
architectures. That native result is still required before dispositioning the
cursor concern. No cursor implementation change or suppression has been made.

Full review at `33243700` completed on September 23 at 03:15 UTC. Comment
4078731455 questions charging two cleanup requests. These are two actual provider
calls, GetBucketVersioning and DeleteObject, so both consume the request budget.
The native integration expectation now includes both calls. Reducing the count
would understate provider usage rather than remove a duplicate request.

The native media checks exposed a separate delete-response error before reaching
the 513-reference PostgreSQL test. The pinned S3 fixture returns an explicit
`x-amz-delete-marker: false` for an unversioned deletion. The adapter incorrectly
treated the header's presence as a delete marker. A failing regression reproduces
that refusal; the correction accepts only literal `false`, while true, malformed
values and any version ID remain UNKNOWN. All 52 focused adapter, mutation and
inventory tests pass after the correction. Fresh native checks remain required;
neither this fixture nor the local regression establishes external provider
retention or writer closure.

Full review at `5a66ea84` completed on September 23 at 03:26 UTC. Comment
4078776283 correctly identifies a provider query before recovery DELETE authority
validation. The adapter now validates and reserves the exact source reference
first, then queries versioning. A refusal settles only the unstarted invocation;
an already closed reference makes no provider call. The invalid-backup regression
failed before this change and passes with all 52 focused checks afterward.

Comment 4078776281 concerns ordinary PUT reading its private upload spool twice.
Both production callers use a unique UploadValidator-owned temporary file and
retain its lifetime until the synchronous adapter returns. Neither caller mutates
or shares that file with another writer. Content-MD5 protects the submitted bytes,
and the later worker checks the expected SHA-256 before availability. Recovery PUT
already uses a bounded private byte copy. No new path permitting concurrent spool
replacement was found; this does not claim protection against a compromised host.

The native media jobs at `5a66ea84` passed the actual adapter/scanner and all 37
inventory checks on PostgreSQL on both architectures, including the 513-reference
streaming/batch regression. That disposes comment 4078647995 against the actual
database. Both then rejected an outdated restart-test call that tried to release
maintenance while UNKNOWN remained. The test now requires release refusal,
preserved maintenance identity, and blocked PUT/DELETE; it never fabricates
settlement. Fresh restart proof remains required.
