---
schemaVersion: 1
id: architecture-review-public-edge-and-admission
revision: 1
type: architecture-review
status: proposed
title: Trusted edge identity and shared request admission
repository: chanter
capabilityIds: ["production-deployment"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Final exact-head hosted acceptance and browser screenshot inspection", "Actual provider firewall, challenge and public edge verification"]
modules: ["gateway-service", "production-edge"]
interfaces: ["trusted-forwarded-identity", "shared-request-admission"]
seams: ["proxy-to-gateway", "authenticated-user-to-request-budget"]
adapters: ["spring-cloud-gateway", "redis", "caddy"]
relatedRecords: ["architecture-review-chanter-free-deployment@1"]
decisions: []
incidents: []
features: []
capabilities: ["Canonical client identity", "Shared sensitive-operation budgets", "Bounded account recovery during admission-store outages"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Public edge and abuse controls", "url":"https://github.com/Vinosaamaa/chanter/issues/253", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["backend/gateway-service/src/test/java/com/chanter/gateway/ProxyBoundaryHttpTest.java", "backend/gateway-service/src/test/java/com/chanter/gateway/security/RequestAdmissionFilterTest.java"]}
visibility: public-safe
publicationEligibility: eligible
issue: 253
pr: 328
release: null
run: null
---
# Trusted edge identity and shared request admission

The gateway previously accepted no explicit trusted-proxy policy and had no
shared request admission. A forwarded address or caller identity must not select
another user's request budget. Resolve the client from the socket, accepting a
single literal forwarded IP only from an exact configured proxy. Remove caller
identity/service headers before JWT authentication. Spring's outbound header
filters remove forwarded headers, so the final request filter reconstructs only
the validated IP and configured public origin.

A WebFilter applies shared IP budgets before authentication. A GlobalFilter then
applies the verified user's global and operation budgets. A tenant hint adds a
user/tenant partition without granting membership or replacing the user-wide
limit. Redis performs atomic check-and-increment operations with expiring fixed
windows. HMAC keys omit raw IPs, user IDs and credentials. Explicit backend
permissions and durable email, AI and storage quotas remain authoritative.

A Redis failure closes ordinary admission with a generic 503 and retry guidance.
Logout and recovery retain a separately counted, process-wide maximum of sixty
requests per minute during that failure, with backend permission and email
controls still applied. This exception is explicit and metered. Health probes
remain independent. Error bodies expose a fresh request identifier and no input
or provider detail.

The design and remaining rollout work are in
`docs/architecture/public-edge-and-abuse.md`. Local gateway HTTP and focused
admission tests pass. Real multi-instance Redis proof, production configuration,
bounded bodies/concurrency, browser policies, optional bot proof and actual
provider tests remain incomplete. This record is a design decision, not launch
acceptance.

