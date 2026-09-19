# Issue #244 review findings

The full CodeAnt review completed for `01c5b4426c04d8f116b5ef9089bc938593ae60fe` on September 18, 2026. Quality and SAST checks are separate from that completed review. This first remediation round follows the rebase onto #319.

| Finding | Disposition and verification |
| --- | --- |
| Optimized Python removes readiness assertions | Fixed. Explicit failures enforce object-store readiness and signature age under `python -O`. Both regression cases failed before the change. Java's independent per-scan age gate remains unchanged. |
| Locale-dependent English scanner date parsing | Explicitly select the C time locale before parsing. The standalone interpreter normally starts with this locale; the selection also covers an embedding caller that changed it. |
| Failed demo resource reused | Fixed. Reuse only processing or available entries. The failed/rejected selection regression failed before the change. |
| Retry sleep exceeds remaining timeout | Cap the sleep at the remaining time. A network operation already in progress still has its separate three-second timeout. |
| Legacy migration retries become scans and delete originals | Not reproduced. `claim` selects `MIGRATE` using the legacy backend, including `SCAN_FAILED` retries. Exhausted legacy failures are excluded from timed cleanup. A regression checks five migration failures, preserved original bytes and reservation, no cleanup job after 24 hours, and successful explicit operator retry. |
| Local adapter symlink check can race filesystem replacement | The local adapter is a development/test adapter with an operator-owned private root and mode 0700 on POSIX. Course users cannot write that root or choose object paths. A process with the media account's filesystem authority can already modify bytes and metadata; this adapter does not isolate mutually hostile processes using that account. Production must select S3. Do not claim the preliminary symlink checks provide race-free isolation against such a writer. |
| Native jobs skip private repositories | Intentional owner-required free-resource guard. This repository is public and both native jobs run. A future private fork must choose an authorized runner and cannot use skipped checks as release proof. |

Verification: `python -O -m unittest discover -s scripts/media -p 'test_*.py'`; affected-module Maven verification; final-head native storage/scanner, backend package security and full CI gates.

## Nonblocking suggestions

Retain the small duplicated Maven commands, current usage response record and explicit SQL state rules. New wrappers or a state framework would add scope without a behavior correction. Key regex caching, cancelled-deadline removal and metadata traversal tuning are optional optimizations at the bounded worker rate.

Retain verified spooling before download. Direct streaming would expose bytes before the stored checksum is confirmed. The 10 MiB upload cap and foreground concurrency cap bound buffer allocations, including validation, ingestion and MD5 generation. The single-worker ingestion protocol, resource-list pagination and batched reconciliation can evolve with their owning capacity and ingestion work; no unbounded file-size path is added here. Local sorted traversal is development-only; production S3 listing is paged.

Retain generic exceptions without provider causes because HTTP/provider exceptions can carry private endpoints or response data. The existing phase/resource tracing identifies deferred work without publishing those details. Retain the proven compressed scanner fixture, which expands beyond the daemon's 40 MiB cumulative and 10 MiB per-file inspection limits while staying below the transport limit when compressed.

## Production boundary

The second review at `8e2670e` also raised deletion after the final download authorization check and reclaiming expired worker leases. The documented download contract permits a download authorized before a later deletion to finish; new downloads and deletion during provider reads remain denied. Expired leases deliberately permit recovery with at-least-once work. A stale lease cannot overwrite the newer lifecycle state; repeated provider attempts are still metered, immutable puts reject replacements and repeated deletes are idempotent. This does not establish exactly-once remote indexing.

The integrated backend-package scan found two HIGH advisories in unused HttpCore 5.3.6 libraries brought by the AWS SDK's new `apache5-client` runtime dependency. The adapter explicitly selects `UrlConnectionHttpClient`; exclude the unused Apache 5 transport alongside the existing Apache 4 and Netty exclusions. Repackage and rerun the complete artifact scan without suppressions.

The second remediation round also requires `aiApproved=true` when reusing a demo seed and propagates an explicit request-budget denial as HTTP 503. Cleanup still retains the byte reservation until deletion is confirmed; uncertain provider writes retain their existing durable failed-resource behavior. Regressions first reproduced the unapproved seed selection and swallowed budget rejection at both adapter and service boundaries. The optimized-Python regressions run under the workflow's actual `python3 -O` invocation; they do not claim subprocess isolation inside each test.

The independent review made two gaps blocking for this change. Retrieval now intersects grants with the live media catalog, which requires `AVAILABLE`, AI approval, the requested course and the viewer's existing permission. Missing or failed metadata cannot authorize content. Vector retrieval, chunk tools (including an earlier scope), and existing pre-provider/pre-publication citation checks share that contract. Injected stale chunks cover processing, rejection, deletion, unapproval, wrong course, instructor access and media failure.

Agent migration V8 stores a permanent deletion marker per resource. Final chunk replacement, migration purge and terminal deletion lock that row in the same local transaction; completed deletion therefore rejects an ingestion that outlives its caller. If chunk replacement already owns the row, deletion waits and then removes its committed chunks and cascading embeddings. No distributed transaction is added. Media releases its reservation only after object cleanup and the committed agent deletion response. Migration uses the separate authenticated purge route; purge never clears a deletion marker. H2 concurrency tests and the native PostgreSQL suite cover both operation orders, clean re-ingestion after migration purge, repeated deletion and marker persistence across process restart. Tombstones retain only UUID/boolean metadata, not resource content; this change does not claim a constant bound on historical metadata rows.

#243 composition, epoch 4, actual private-provider recovery and measured full-stack capacity remain required. #246/#251 retain broader ingestion/capacity work, not these corrected authorization and terminal-deletion gaps.

The follow-up full review found that direct embedding backfill did not acquire the lifecycle lock. Foreign keys prevented retained deleted content, but the race could fail with a database error. Both the terminal-backfill and active-backfill/deletion regressions failed before the correction. Backfill now acquires the same resource lock before reading chunks or computing embeddings, holds it through its transaction and returns the explicit conflict for a retired ID. Native PostgreSQL runs inherit both regressions.

The same review identified the outdated internal retrieval example. Updated `local-embeddings.md` with required course/viewer fields and explicit HTTP 400 migration behavior. All repository callers already pass both fields; retaining the old request shape would bypass the new authorization boundary. The per-resource chunk-query loop predates this change; the new authorization intersection only reduces its input set. Batched retrieval remains a capacity improvement under #246/#251, not a newly introduced correctness regression.
