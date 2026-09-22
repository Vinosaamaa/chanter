---
schemaVersion: 1
id: architecture-review-operational-monitoring
revision: 1
type: architecture-review
status: proposed
title: Bounded operational metrics and alert delivery
repository: chanter
capabilityIds: ["production-deployment"]
createdAt: 2026-09-19
reconstructed: false
confidence: high
unknowns: ["Final combined production monitoring proof", "Error tracker and private source-map integration", "Actual free-provider quotas, dashboards and operator alert receipt"]
modules: ["shared-observability", "durable-events", "production-runtime"]
interfaces: ["private-telemetry-export", "queue-health-measurements"]
seams: ["source-to-metric-sampler", "application-to-telemetry-provider", "provider-to-operator"]
adapters: ["opentelemetry", "micrometer", "jdbc"]
relatedRecords: ["architecture-review-observability-and-recovery@1"]
decisions: []
incidents: []
features: []
capabilities: ["Private business metrics", "Truthful queue collection health"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Operational monitoring and alerts", "url":"https://github.com/Vinosaamaa/chanter/issues/331", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["backend/telemetry/src/test/java/com/chanter/telemetry/NativeTelemetryExportTest.java", "backend/telemetry/src/test/java/com/chanter/telemetry/SafeMetricExporterTest.java", "backend/common/src/test/java/com/chanter/common/telemetry/QueueMetricsTest.java", "backend/common/src/test/java/com/chanter/common/telemetry/QueueMetricQueryTest.java", "backend/common/src/test/java/com/chanter/common/events/OutboxMetricsTest.java"]}
visibility: public-safe
publicationEligibility: eligible
issue: 331
pr: 334
release: null
run: null
---
# Bounded operational metrics and alert delivery

PR329 supplied private runtime export but did not connect existing application
counters. A real Spring Boot fixture reproduced the missing email measurements.
The pinned agent's Micrometer bridge now exports fixed email/admission counters,
retaining bounded outcome dimensions while removing private labels before
aggregation. A second test prevents duplicate JVM metrics from the bridge.

Durable-event gauges read a cached aggregate. Their independent sampler never
queries the database from product request or telemetry export callbacks. Failed
collection preserves the prior observation with unhealthy status; observations
older than 90 seconds become unknown. Successful empty queues remain distinguishable
from unavailable collectors. The SQL helper sets statement and socket deadlines
and restores the original connection setting. H2 timeout, connection-return and
queue transition tests pass. PostgreSQL cancellation passed on both hosted architectures.
The real Boot fixture now proves enabled queue-gauge export while its application
is alive, with a receiver acknowledgement before shutdown. Disabled monitoring is
tested separately. Email and resource collectors pass their source-state tests
on both hosted architectures. Resource age describes pending-row
inactivity because claim and retry transitions reset its existing timestamp.

AI observations follow committed reservations and successful settlement writes,
with fixed outcomes and measured/unmeasured usage. They do not replace durable
accounting, and native execution contributes no artificial zero duration.
Rollback, duplicate settlement, unavailable metrics and actual Boot timer export
are tested. The realtime gauge uses the existing local connection map; a real
authenticated WebSocket test proves connection and disconnect counts.

There are no new public endpoints or authorization grants. Metric names and
dimension values remain explicitly bounded by the existing private exporter.
The source query accepts only source-owned SQL, never caller-supplied query text.
Connection acquisition uses the existing pool's bound; its wait is not covered
by the SQL statement deadline.

The [design](../../architecture/operational-monitoring.md) and
[implementation record](../../operations/issue-331-change-log.md) distinguish this
tested coverage from the unfinished final combined proof, dashboards, exception
transport, private source maps, operational runbooks and
actual provider notification. Free quotas and disabled paid overage must be
verified in the eventual accounts. This proposed system review does not claim
operator alert delivery, full recovery or public launch.
