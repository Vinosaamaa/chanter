# Operational monitoring and alert delivery

Issue #331 owns the unfinished operational coverage left open by #252. The accepted
PR329 foundation filters telemetry before private export and proves isolated
database recovery. It does not yet export the product's business instruments,
deliver exception reports, or alert an operator during a real outage.

## Application metrics

Reuse the pinned Java agent's Micrometer bridge, which is disabled by default.
The first acceptance test starts a real Spring Boot application, registers its
existing email/admission instruments, and decodes their exported metrics. Admit
only fixed names, units and finite outcome dimensions. Unknown attributes are
removed before aggregation; invalid residual values reject the metric. Keep
runtime pool dimensions so distinct gauges do not collapse into an arbitrary
value. Export must exclude account IDs, content, URLs and exception messages.

Queue metrics belong to their source modules. Collect pending work, failures and
oldest age with a bounded query outside product request paths. Export collection
health and sample age separately. An unavailable or stale collector must never
report a healthy zero queue. Preserve actual failure counts and unknown AI usage;
an accounting reservation is not measured provider consumption.

The implemented event collector includes pending and claimed events. Email counts
pending messages and retained expired-message metadata, which the existing worker
purges after seven days. Resource counts include upload, scan, indexing and cleanup
work, with scan/index failures shown separately. Resource age is elapsed time
since the oldest pending row's last update, not its total queue wait or original
upload age. Retry and claim transitions reset that activity timestamp. No content,
recipient, resource ID or model name is a metric dimension.
Legacy resources count as pending only while the existing legacy migration worker
is enabled. An inactive migration must not create a fictitious backlog.

The real Boot proof keeps the enabled collector and its gauges alive until the
private test receiver acknowledges a pending database row and successful collection.
It also checks counter privacy and disabled configuration separately. Native
PostgreSQL cancellation and the email/resource source queries have passed on both
hosted architectures. Live WebSocket counting is tested with an authenticated
socket and removal after disconnect, without account or session labels.

AI instruments describe committed operational observations. `chanter.ai.requests`
counts committed reservations, including requests later proven not started.
`chanter.ai.settlements` counts successful settlement writes by fixed outcome and
measured/unmeasured usage; a later reconciliation of UNKNOWN is another settlement,
not another request. `chanter.ai.unmeasured_settlements` counts attempted settlements
without measured input/output usage. It is not a current unresolved balance.
`chanter.ai.duration` records positive server-observed elapsed time in seconds;
native client execution with unknown duration contributes no artificial zero.
The durable usage ledger remains the accounting authority, including reservations
left by crashes. Telemetry can be lost during process failure, and rollback emits
no observations. A metrics failure cannot change a committed operation's result.

Critical operations include durable events, transactional email, resource scan
and ingestion, AI generation, realtime connections and gateway admission. A
private receiver outage must leave application behavior unchanged, use bounded
memory and allow process shutdown. Final native load tests must include enabled
export, not just an attached agent with both exporters disabled.

## Exceptions and operator alerts

Select a supported free error tracker only after verifying its current account
entitlements. Sentry's published Developer tier is a candidate for exception
reports, private source maps, one uptime monitor and one backup heartbeat monitor.
Grafana Cloud is a candidate for existing OTLP metrics/traces and dashboards.
Neither account nor actual notification delivery exists yet. No paid trial,
automatic recharge, replay, profiling or content-bearing attachment is required.

Exception transport must rebuild a bounded safe event containing the immutable
release, exception type and application frames. Remove messages, request bodies,
headers, identifiers and breadcrumbs. Private source maps must match the release
and remain absent from publicly served assets. Test canary removal, overload,
disabled configuration and receiver failure before enabling the integration.

The backend implementation pins Sentry Java 8.57.0 and attaches a Logback appender
only when explicitly enabled. It constructs a fresh event from ERROR exceptions,
keeping at most four causes and twelve application frames per cause after scanning
at most 64 frames. It discards plain messages, filenames, exception text, MDC,
request/user data and original Throwable objects. The direct SDK client has no
global scope, default processors or integrations, breadcrumbs, attachments, disk
cache, replay, profiling, logs or metrics export. Only the immutable release,
fixed service/environment, exception types and code locations leave this boundary.
The real SDK envelope test verifies the resulting wire payload.

Each process admits five reports per monotonic minute, then drops additional
reports until the next window. The transport queue holds at most 32 events with
one-second connection/read limits and a 1.5-second shutdown limit. This is a local
overload bound, not an account-wide quota or a guarantee of error completeness.
The provider must enforce its free quota and disable paid overage. Restarting a
process resets its allowance. Disabled configuration needs no credentials.
Production accepts only a validated HTTPS Sentry receiver; the test suite alone
uses an isolated loopback receiver. This does not prove delivery to a real account.

Check dashboards and alert queries against recorded synthetic measurements. Each
alert needs a severity, accountable operator, trigger, recovery condition and
linked action. Independent uptime and missing-backup-heartbeat checks must work
when the application host is down. Launch requires actual provider-side receipt
and operator notification, verified free quotas and disabled paid overage.

## Verification and remaining decisions

The application metric bridge, queue collectors and initial AI/realtime coverage
and backend error transport are implemented. Final combined proof, dashboards,
frontend error capture, source-map
publishing, incident/rotation runbooks and provider acceptance remain unfinished. #332 separately owns complete
application restore with current deletion authority. Neither issue is closed by
the successful database-only restore in PR329.

Primary references:

- [Pinned Java agent supported libraries](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.31.1/docs/supported-libraries.md)
- [OpenTelemetry Java instrumentation](https://opentelemetry.io/docs/languages/java/instrumentation/)
- [Sentry plan capabilities](https://sentry.io/pricing/)
- [Pinned Sentry Java release](https://github.com/getsentry/sentry-java/releases/tag/8.57.0)
- [Grafana frontend pricing boundaries](https://grafana.com/docs/grafana-cloud/platform/pricing-and-usage/frontend-observability/)
