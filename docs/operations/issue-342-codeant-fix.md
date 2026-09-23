# Issue #342 review dispositions

Full CodeAnt review completed for `96d807c9a220286b7a2fc6a4d9bcef3d88cc0b6d`.
This draft remains blocked on the accepted #251 source union and full recovery
proof. The recovery capability stays OFF. Passing the current tests does not
remove those dependencies.

## Findings

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
