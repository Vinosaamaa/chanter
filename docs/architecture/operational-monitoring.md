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

The real Boot proof keeps the enabled collector and its gauges alive until the
private test receiver acknowledges a pending database row and successful collection.
It also checks counter privacy and disabled configuration separately. Native
PostgreSQL cancellation passed on both architectures at the initial draft head;
the new email/resource queries still require their extended native run.

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

Check dashboards and alert queries against recorded synthetic measurements. Each
alert needs a severity, accountable operator, trigger, recovery condition and
linked action. Independent uptime and missing-backup-heartbeat checks must work
when the application host is down. Launch requires actual provider-side receipt
and operator notification, verified free quotas and disabled paid overage.

## Verification and remaining decisions

Implementation is beginning with the real application metric bridge. Source
collectors, dashboards, error transport, source-map publishing, incident/rotation
runbooks and provider acceptance remain unfinished. #332 separately owns complete
application restore with current deletion authority. Neither issue is closed by
the successful database-only restore in PR329.

Primary references:

- [Pinned Java agent supported libraries](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/v2.31.1/docs/supported-libraries.md)
- [OpenTelemetry Java instrumentation](https://opentelemetry.io/docs/languages/java/instrumentation/)
- [Sentry plan capabilities](https://sentry.io/pricing/)
- [Grafana frontend pricing boundaries](https://grafana.com/docs/grafana-cloud/platform/pricing-and-usage/frontend-observability/)
