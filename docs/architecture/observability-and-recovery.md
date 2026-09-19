# Production observability and recovery

Status: proposed implementation for #252. No production export, alert receipt or
off-host recovery has been observed. The free deployment is a single host with
PostgreSQL and private S3-compatible objects; it is not a managed database.

## Outcomes and boundaries

An operator must identify a failing customer journey without collecting its
messages, files, credentials or AI prompts. A lost host must be replaceable from
off-host data and separately recoverable configuration. An application rollback
does not restore a database. Restores must use a new, isolated destination and
verify source databases, durable event delivery, resource metadata and object
availability together before a cutover.

The initial operational targets are a five-minute database recovery point and a
four-hour recovery time. These are design targets, not measured guarantees. Actual
provider drills must establish or revise them before #255 can accept launch.

## Database recovery

Use pgBackRest 2.59.1 in the existing PostgreSQL 16 image, built from its published
source archive and pinned SHA-256. It owns full/incremental backups, continuous
write-ahead-log archiving, encryption and point-in-time recovery. Do not implement
a second database backup format or an ad hoc SQL-dump restore protocol.

Production uses a separate private S3 backup bucket/prefix, verified TLS and
credentials restricted to that repository. The repository is encrypted on the
client with a distinct generated passphrase; losing that passphrase loses the
backup. Keep its recovery copy outside the application host and outside the
repository it encrypts. Do not put credentials in command arguments or logs.

Use one backup worker and low compression to fit the free host. Archive completed
WAL segments continuously, with a bounded PostgreSQL archive timeout. Backups run
on a host timer with mutual exclusion: weekly full, daily incremental. Retain
two successful full backup chains with their required WAL. Failed new backups do
not make the last recoverable chain eligible for deletion. Observe archive delay,
backup age, repository size and host free space; archive failure must alert before
retained WAL exhausts the host.

The restore command accepts a reviewed backup/target and a new empty destination.
It must reject a live data volume, an existing nonempty directory, path traversal
and symlink substitution. Restored test clusters disable archive publishing and
external service delivery. Their data never overwrites the production repository.
A drill inserts committed markers around a recovery target, takes an encrypted
backup, archives WAL, restores a second real PostgreSQL cluster and checks both
included and excluded commits. A second application-level drill checks the
accepted service schema and durable source/consumer invariants.

## Objects and configuration

Database recovery alone cannot recover resource bytes. Production object storage
must have version/lifecycle protection and a reviewed retention window at least
as long as the database recovery window. A recovery inventory records object key,
version/checksum and source state without object contents. Never rewrite live
object versions while rehearsing recovery. Terminal deletion records and access
checks remain authoritative after restore; a recovery must not expose data that
was deleted or access that was revoked after the selected recovery point.

Runtime configuration and secrets need their own encrypted recovery artifact,
bound to a release/configuration manifest. The decryption key is kept outside
that artifact. Native signer, operator encryption, database and session keys must
be included when those features are enabled. Backups and retention must be
reconciled with #251; no unsupported immediate-erasure claim belongs in policy.

## Telemetry and operator access

Use OpenTelemetry for correlated server, outbound HTTP, database, background job
and realtime-handshake traces. Export is sampled and bounded; exporter failure
must not block customer requests. Use the agent's metric SDK for request
duration/status and JVM/pool pressure, adding bounded instruments
for event lag, email, scan/ingestion, AI outcome/usage and recovery state. A
disabled paid-billing mode is reported explicitly rather than inventing webhook
traffic.

Use the pinned OpenTelemetry Java agent with a small exporter privacy extension.
Existing services construct both Spring and raw JDK HTTP clients, so a Spring-only
starter would leave material call paths untraced. The extension uses the agent's
own SDK and is isolated from application dependencies. It exports an allowlist of
HTTP methods/statuses, database operations and release/service/environment metadata.
Raw names, routes, attributes, events, links and incoming vendor trace state are
discarded. Public ingress removes client-supplied tracing and baggage headers.
The real-agent test decodes OTLP, verifies client/server trace continuity and
asserts that planted secret/content values never reach the receiver. Metrics use
the same private receiver at its sibling `/v1/metrics` endpoint, with a sixty-second
interval. Both signals remain disabled without explicit receiver configuration;
log export stays disabled.

The metric boundary accepts fixed operational names and units. SDK views remove
private dimensions before aggregation, so many account or URL values cannot split
a metric into private series or consume the series limit. The exporter rejects
unknown dimensions and invalid residual values; names, descriptions, resources,
scope identity and exemplars cannot carry arbitrary text. It preserves bounded
JVM memory pool/type and thread state/daemon dimensions: collapsing asynchronous
gauges would retain one value instead of correctly reporting distinct pools.
Unknown future pool names fail closed until reviewed. The real-agent test records
600 distinct private account values and verifies one histogram with 600 samples,
with no canary anywhere in decoded OTLP. Business outcome instruments, dashboards
and actual alert delivery remain separate implementation and provider gates.

Production log formatting retains logger, severity, immutable release, trace IDs
and bounded application exception frames. It never serializes free-form messages,
arguments, arbitrary MDC or exception messages. This intentionally omits third-party
diagnostic text; stable operational outcome metrics and explicit application events
must supply actionable detail. Developer logging is unchanged outside the release.

Structured records identify environment, service, immutable release, request and
trace IDs, safe operation/outcome and an authenticated actor only where needed.
Exclude raw URLs/query strings, headers, cookies, tokens, message/file contents,
SQL values and provider prompts/responses. Exception export needs class and
sanitized application frames plus release/source-map linkage, not arbitrary
exception messages or browser breadcrumbs. A canary test must prove that secrets
and content do not leave either server or browser exporters.

Use configured export endpoints with explicit credentials; missing configuration
must be visible as disabled, never reported as working monitoring. Do not publish
actuator/metrics/debug endpoints through the public gateway. Alerts need a named
owner, severity, runbook and tested receiver. Host recovery/backup failure must
remain observable when the application itself is down. Provider accounts and
actual notification receipt are external acceptance gates.

## Verification and delivery

First verify unsafe configuration rejection and the real encrypted PostgreSQL
restore on AMD64 and ARM64. Then verify sampled trace continuity, exporter outage,
privacy canaries, operational gauges and frontend exception/source-map linkage.
Run the complete product suite against the resulting runtime package. Record
actual drill outcomes separately from configured targets. Keep #252 open through
provider alert and off-host restore proof.

References: [pgBackRest guide](https://pgbackrest.org/user-guide.html),
[repository encryption and retention](https://pgbackrest.org/configuration.html),
[Spring Boot observability](https://docs.spring.io/spring-boot/4.0/reference/actuator/observability.html),
[OpenTelemetry instrumentation](https://opentelemetry.io/docs/zero-code/java/agent/instrumentation/).
