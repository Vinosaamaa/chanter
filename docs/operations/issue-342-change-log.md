# Issue #342 implementation checkpoint

Draft PR #343 owns this issue. PR #336 is accepted with recovery disabled. This issue owns the remaining real
source and private-object recovery proof; the accepted #332 worktree is preserved.

The first slice adds media V6 and an owning storage-mutation store. New physical
operations and maintenance fencing serialize on the existing storage-budget row.
Outstanding operations survive service recreation, unknown deletes block
same-key retry, releasing maintenance does not erase uncertainty, and unresolved
records have a fixed capacity bound. Only an owning invocation's definitive
completion may remove its record. This store does not dispatch work or retry it.

The first three H2 transaction regressions failed before implementation and then
passed. Six focused checks now cover the outstanding-operation bound, source
transaction requirement and adapter transaction boundary. The added boundary
test first reproduced a lock timeout when a suspended source transaction already
owned the budget row. Independent accounting now rejects an active source
transaction before opening another transaction; all six checks pass. Five existing
media lifecycle/context checks also passed before this boundary change. Native
PostgreSQL and the actual adapter call-chain proof remain required.

Both private adapters now require the store and account for every PUT/DELETE,
including calls from orphan cleanup. Pre-dispatch refusal reports NOT_STARTED;
remote timeouts/5xx remain UNKNOWN, and only definitive invocation completion
settles the record. The actual local adapter regression first failed because it
wrote inside the source transaction, then passed with no file or mutation record.
Thirteen focused checks pass, including actual loopback S3 failure, adapter/store
restart, same-key refusal without redispatch, definitive DELETE/404 settlement,
maintenance refusal and Spring namespace wiring. Fixtures are hermetic and do not
establish actual provider closure.

Full affected media/common Maven verification passes at the adapter checkpoint.

The next disabled metadata slice adds one bounded, temporary source-owned
inventory snapshot and private fence/capture/page/discard routes. Exact applied
authority, beyond-prefix targets, unknown mutations/source writes, leases,
namespace and current/derived server scope are checked in terminal-before-budget
lock order. Provider version remains null and source reservation is described as
`sourceRetained`, not physical byte evidence. There is no maintenance release or
byte-write endpoint. Exact final affected-module verification passes, including
six inventory, four controller/absence and ten mutation/adapter checks. The media
suite reports 95 tests, zero failures/errors and five native-only skips. Independent
read-only review found no concrete query/API/migration blocker and preserved the
explicit source-union, PostgreSQL, bytes and external-closure limitations.

The internal maintenance PUT slice validates the saved snapshot/backup/prefix and
exact current source tuple, then reserves one mutation while retaining the global
fence. Three authorization regressions failed against the unimplemented boundary,
then passed. The adapters clone and verify bounded bytes and use existing
create-only writes. Actual local and loopback S3 byte read-back, overwrite refusal,
terminal/changed tuple refusal and UNKNOWN completion tests pass. Full affected
media/common verification passes with 100 media tests, zero failures/errors and
five existing native-only skips. No byte HTTP endpoint is enabled.

The existing dual-architecture media workflow now runs the query contracts on
PostgreSQL and preserves an UNKNOWN DELETE/fence through its actual database
restart. Both native architectures passed at `96d807c9`, including the query
contracts and actual PostgreSQL restart. These synthetic source-row
contracts remain distinct from the actual #251 canonical fixture and full object
archive/restore orchestration.

The completed full CodeAnt review at `96d807c9` found a local failed-copy residue.
The regression reproduced an object remaining after its input could not be read.
The adapter now removes only the file it successfully created for that invocation,
after stream closure. Cleanup failure retains UNKNOWN; an existing-key conflict
cannot delete another object. The regression then passed with the affected
inventory and adapter tests. Review dispositions and outstanding source-union
blockers are recorded in `issue-342-codeant-fix.md`.

Coordinated source lifecycle hooks, physical inventory qualification, full object
restoration and actual #251 canonical replay are still in progress.
The store alone proves no provider closure. No recovery capability, source API,
provider account or public cutover is enabled by this checkpoint.
