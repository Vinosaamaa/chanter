# Launch monitoring and incident response

Issue #331 owns these rules and dashboards. They are proposed operational settings
with offline tests, not evidence that a provider account or notification channel
exists. Public cutover requires an operator to receive a forced alert and its
recovery notice outside the application host. The `launch-operator` label is a
role that must be assigned to a named account before launch.

## Initial service objectives

Target 99% availability over 30 days for sign-in, reading/sending messages and
opening authorized resources. Target ordinary API response p95 below two seconds,
transactional email acceptance within two minutes and durable events within five
minutes. Upload scan/index progress may take fifteen minutes before investigation.
AI requests must finish or return an explicit unavailable/handoff outcome within
the configured runtime deadline. These are initial targets, not achieved results
or guarantees of SMTP inbox delivery, provider inference or media quality.

The current metrics deliberately omit route and user identifiers. Aggregate
gateway latency includes health and long-running traffic; it cannot establish a
sign-in or upload objective by itself. Critical browser journeys and two-client
audio require their separate synthetic tests and production acceptance. Zero
traffic is not success. Missing series remain unknown, never green zeros.

## Provider setup and validation

1. Verify the free account's active-series, ingest, retention, notification and
   monitor quotas. Disable paid overage and automatic upgrades. Keep the receiver
   token in the private telemetry environment file and configure only HTTPS.
2. Import `infra/monitoring/operations-dashboard.json` into a private Grafana
   workspace and choose its Prometheus data source. Import `alerts.yml` into its
   Prometheus-compatible rule service. A label `owner=launch-operator` must route
   to the operator's verified email. Group by alert name and service, wait one
   minute, group subsequent messages for five minutes and repeat unresolved
   critical alerts hourly. Send resolved notifications.
3. Check actual received metric names and labels before enabling rules. This
   package assumes Grafana's standard OTLP suffix conversion and promoted
   `service_name` / `deployment_environment_name` labels. Queue count/health gauges
   currently have unit `1`, hence the provider's `_ratio` suffix. Their dashboard
   units still display counts or healthy/unhealthy. Native receiver tests prove
   the OTLP input; actual provider translation remains an acceptance gate.
4. During staging, substitute the rule environment explicitly. Force an unhealthy
   queue collector and a receiver interruption without real user data. Record the
   alert, operator receipt and resolved notification; restore ordinary settings.
   Confirm production rules exclude staging. Verify all ten expected services.
5. Configure an independent external HTTPS probe and missing-backup-heartbeat
   monitor that run when the host is off. The probe must check the owned hostname,
   valid certificate and expected success response. Prefer the public auth
   verification-options endpoint so the check reaches gateway/auth, rather than
   measuring only static-file delivery. This is not a full user journey or proof
   of every source database. Inspect the actual provider's supported assertions.
6. Create Sentry cron monitor `chanter-backup-verification` with a ten-minute
   interval and 190-minute grace. The grace covers the existing exclusive
   three-hour backup deadline plus scheduler delay; it deliberately does not
   promise immediate failure detection. Configure the operator notification and
   resolved notice. Copy its HTTPS ingest URL into `CHANTER_BACKUP_HEARTBEAT_URL`
   in private `runtime/errors.env`, then redeploy. The URL ends in
   `/api/PROJECT/cron/chanter-backup-verification/PUBLIC_KEY/`; never post it.
   The owning backup command sends success only after verifying a fresh completed
   chain and matching encrypted configuration. A failed verification sends no
   success. A two-second receiver failure preserves the successful backup and
   records `unconfirmed` in a separate private `backup-heartbeat-status.json`.
   Ingestion acceptance alone does not prove provider processing or notification.
7. Force a missed heartbeat in a separate staging monitor and receive its alert
   and recovery notification. Then prove that the production monitor and HTTPS
   probe still alert with the host unavailable. Check actual free monitor and
   environment quotas before configuring both environments. None of those
   account-side receipts exists yet.

The metric conversion contract is documented by
[Grafana OTLP format considerations](https://grafana.com/docs/grafana-cloud/observe-and-act/send-data/otlp/otlp-format-considerations/).
The heartbeat follows [Sentry's HTTP cron integration](https://docs.sentry.io/product/monitors-and-alerts/monitors/crons/getting-started/http/).
Offline alert fixtures run through the pinned Prometheus `promtool test rules`
command. They test rule behavior; they do not prove email or provider delivery.

## Missing telemetry

The critical alert fires when fewer than ten configured services have exported
memory data in five minutes and that condition persists for two minutes. Check
the independent uptime probe first, then private provider status and the dashboard
inventory. A working site with missing telemetry is a monitoring incident. A host
outage requires the deployment/recovery procedure, not repeated exporter restarts.
Inspect service health and safe structured exception types/code locations. Never
paste raw configuration, request logs or `docker inspect` environment data.

## Queues and email

An unhealthy collector means the queue value cannot be trusted. Check database
readiness, pool saturation and sampler status before diagnosing backlog size.
An old email queue requires checking SMTP credentials, TLS, free quota and the
sender's verified status. Do not replay expired verification/reset links. The
existing worker owns retry and expiry; do not manually duplicate queued messages.
SMTP acceptance is distinct from inbox receipt. Durable event failures require
checking the destination's health and schema compatibility. Preserve outbox rows
and idempotency keys while repairing the cause.

## Resources and AI

Resource age follows the source row's `updated_at`. State/index transitions reset
it, lease-only retries do not; it is not total upload age or an attempt heartbeat.
Check scanner health, object-store access, disk capacity and ingestion outcomes.
Quarantined resources must stay inaccessible during an outage. Do not bypass scan
or current authorization to clear a backlog.

AI settlement panels describe successful ledger writes. UNKNOWN or unmeasured
usage is not zero usage or a billing receipt. Do not automatically repeat a
provider operation after uncertain completion. Disable optional generation with
the existing kill switch if necessary and preserve requests for reconciliation.
Local semantic retrieval and an explicit handoff remain separate outcomes.

## Gateway and capacity

The critical gateway rule needs more than five-percent 5xx responses and at least
twenty requests per five-minute window for five minutes. A quiet failed service
is covered by missing telemetry/external probing, not this traffic gate. Inspect
readiness, upstream failure, Redis admission and recent deployments. HTTP 429 is
a separate admission signal; do not disable abuse limits to suppress alerts.

Heap pressure above 85% for ten minutes is a warning. Confirm actual container
memory, pool pending requests, CPU and disk before changing limits. The dashboard
shows JVM heap, not total host or container memory. Pause enrollment or expensive
work before exceeding the free allocation. A schema-incompatible release requires
fix-forward or reviewed restore; use only the deployment tool's accepted rollback
path. Never delete data volumes to recover capacity.

## Incident ownership and closure

The operator acknowledges critical incidents within fifteen minutes during the
declared launch support hours. Until a second operator is assigned, escalation
means notifying the product owner and pausing affected enrollment/features.
There is no claim of continuous staffing or a second responder. Investigate
warnings during the next staffed review; escalate confirmed data exposure, loss,
authorization failure or broad outage immediately.

Open one incident record with release, affected capability, start time, safe
evidence, mitigation and recovery proof. Preserve logs/receipts privately. Close
only after the affected user journey passes and monitoring sends a resolved
notice. Record the cause and a regression test. Provider failure requires checking
its official status and quota, preserving uncertain operations and using an
explicit unavailable state. Do not create paid capacity automatically.

## Compromised-secret rotation

Identify the exact credential and revoke it at its owning provider. Pause the
affected capability if containment needs downtime. Generate replacement material
outside chat, update only the private owning runtime file, and redeploy through
the existing locked deployment command. Verify new access, rejected old access,
the user journey and updated encrypted configuration backup. Keep incident notes
free of both old and new values.

SMTP, storage, telemetry and error credentials have separate scopes. JWT/internal
service keys require a coordinated service restart and session/in-flight impact
review. Database/Redis credentials require changing the server-side credential
and its owning clients together. Native issuer rotation must update the reviewed
public verifier installation and prove the old key is rejected. Never claim
rotation complete after editing a file alone. Actual provider rotation drills
remain unverified until accounts and the running production installation exist.
