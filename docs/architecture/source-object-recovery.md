# Source and object recovery

Issue #342 continues #332 after the disabled infrastructure in PR #336. The
release capability remains off. #251 owns canonical deletion and the actual
three-target fixture; its synthetic unit-test journal helper cannot supply
recovery evidence.

## Physical mutation closure

The source's `storage_write_settled` marker covers upload and migration PUTs.
It does not cover failed remote DELETEs or orphan cleanup. A failed DELETE may
clear its worker lease while the provider can still finish that operation.
Stopping the process cannot prove closure of either kind of remote request.

Media V6 adds a maintenance fence to the existing storage-budget singleton and
a bounded table of outstanding physical operations. Both local and S3 adapters
must commit an operation identity before every PUT or DELETE, including orphan
cleanup. New operations lock the same budget row as the maintenance fence.
The adapter removes only its own operation after definitive completion. A
timeout, uncertain remote failure or lost completion record remains outstanding
across restart. Another request for the same key cannot erase that uncertainty.
This is ownership accounting, not a second dispatch or retry system.

Fencing is durable and has no timer that silently resumes writes. It prevents
new physical dispatch while allowing an already dispatched operation to record
its actual outcome. A fence receipt reports outstanding local records only; it
does not call writers quiescent or prove that an older binary, another host or
an untracked historical provider request has stopped. Independent original-writer
and provider closure remains mandatory.

The planned ResourceLifecycle hooks block new reservations, claims and migration
dispatch under its existing terminal-authority then budget lock order. Terminal
access denial and explicit operation settlement remain available. A changed
terminal prefix invalidates inventory qualification. These hooks require the
#251 owner's exact-method coordination before edits.

## Inventory and object coverage

Media remains the catalogue. A private bounded inventory must bind a maintenance
identity, known storage namespace, applied terminal authority and database backup
identity. Qualification requires no outstanding physical operations, no unknown
source PUTs and no unresolved leases or migration references. Pages include
private and quarantined resources and explicit terminal disposition. They must
account for both the current storage key and every distinct retained migration
reference; an unresolved legacy key or missing source hash is a failure, not an
omitted row. Bucket listing and account export cannot establish ownership.

Each restorable tuple uses the source's immutable key, size and SHA-256. Provider
version ID stays null while unknown. The initial supported policy uses immutable
create-only keys and exact encrypted copies linked to each retained database
point; it makes no claim to recover hidden provider history. A namespace whose
required recovery history depends on unrecorded versions is unsupported until
the owning source records and verifies those references. Provider versioning,
retention, capacity and old-writer assumptions need actual provider evidence.

Capture must verify every eligible object's complete encrypted read-back before
publishing its inventory/database linkage. Recovery first applies current terminal
authority, then restores only still-eligible tuples using create-only writes.
Existing destination bytes must match completely. Terminal objects stay
ineligible; quarantine state and live authorization stay source-owned. Missing
copies, corrupt bytes, unknown physical operations or incomplete references keep
the capability off. The existing ResourceObjectArchive remains the byte-verifying
component, not a replacement catalogue.

## Verification and dependencies

First test durable fencing and mutation accounting against real owning database
transactions, including concurrent begin/fence, restart, unknown PUT/DELETE and
same-key retry. Then test the local and S3 adapter boundaries with bounded
hermetic fixtures. A successful API response or an expired worker lease is not
closure evidence.

The accepted #251 union must precede final migrations and real source recovery
gates. Its media V5 owns lifecycle tables; #342 reserves V6. The final hosted
PostgreSQL drill must use real normal deletion paths for ACCOUNT, STUDY_SERVER
and RESOURCE, both current and separately derived historical scope, all seven
participant receipts and both durable invalidations. Repeat the full object
capture/restore and failure cases on AMD64 and ARM64. Local fixtures do not
satisfy the external provider, original-writer or public-cutover gates.
