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

Coordinated source lifecycle hooks, inventory qualification, full object
restoration and actual #251 canonical replay are still in progress.
The store alone proves no provider closure. No recovery capability, source API,
provider account or public cutover is enabled by this checkpoint.
