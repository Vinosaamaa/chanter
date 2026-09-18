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

Deletion can race indexing that continues after the caller times out. A completion lease protects media metadata but does not prevent late AI chunks. Issues #246/#251 must provide durable ingestion/deletion coordination, and retrieval must require a currently `AVAILABLE`, approved resource before using chunks. This is an unresolved production gate, not a completed deletion guarantee. #243 composition, epoch 4, actual private-provider recovery and measured full-stack capacity also remain required.
