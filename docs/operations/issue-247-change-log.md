# Issue #247 implementation evidence

The worktree starts from accepted #246 at f1bf3e8. Existing #247 and historical #95/#96 cover this scope; no duplicate issue or worktree was created. Agent V13+ is reserved after #316 V11/V12. The #252 owner retains production PostgreSQL/runtime changes and will integrate the verified pgvector build and privileged extension bootstrap.

The real-model adapter test failed before the implementation existed, then passed both asset-integrity rejection and real paraphrase ranking. The pinned all-MiniLM-L6-v2 ONNX model uses one inference thread, bounded input/token length and one concurrent native session. A preliminary native Windows probe measured 126 ms p50 and 172 ms p95 for 256-token inputs; those measurements do not establish ARM64/container memory feasibility.

Version-store tests failed before the new schema/store existed. They now verify incomplete coverage blocks candidate activation, model identity/dimension cannot be reassigned, backfill preserves the old version for rollback, and terminal deletion rejects late candidate publication. Scoped SQL tests likewise failed before implementation and now reject wrong Course, Study Server, Cohort, source checksum and dimensions, while authorized results survive a larger unauthorized set. These tests currently use H2; native PostgreSQL and real-model CI proof remain pending.

This draft is incomplete. Production provider selection, retrieval wiring, durable re-embedding execution/progress, model-specific indexes, native relevance/load/memory evidence, final #316 union and complete hosted review gates remain required. No production semantic capability or runtime budget claim is made from the current prototype.
