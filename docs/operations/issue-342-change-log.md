# Issue #342 implementation checkpoint

PR #336 is accepted with recovery disabled. This issue owns the remaining real
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

Adapter tracking, coordinated source lifecycle hooks, inventory qualification,
full object restoration and actual #251 canonical replay are still in progress.
The store alone proves no provider closure. No recovery capability, source API,
provider account or public cutover is enabled by this checkpoint.
