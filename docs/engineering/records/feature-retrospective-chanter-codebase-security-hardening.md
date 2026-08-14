---
schemaVersion: 1
id: feature-retrospective-chanter-codebase-security-hardening
revision: 1
type: feature-retrospective
status: released
title: Service identity hardening across Chanter boundaries
repository: chanter
capabilityIds: ["service-identity-hardening"]
createdAt: 2026-07-16
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and complete workflow logs were not quoted.","No public launch was evidenced; release truth remains strong local beta."]
modules: ["common authentication","gateway service","application services","realtime service"]
interfaces: ["public HTTP identity","inter-service HTTP identity","WebSocket authentication"]
seams: ["gateway-to-service identity propagation","service-to-service identity propagation","WebSocket upgrade authentication"]
adapters: ["RequestIdentity","authenticated request filters","inter-service HTTP clients","RealtimeWebSocketHandler","JwtAuthenticationGlobalFilter"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Reject header-only user impersonation","Validate browser and gateway identity in each service","Authenticate realtime connections with validated tokens"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Issue #188","url":"https://github.com/Vinosaamaa/chanter/issues/188","kind":"issue"},{"label":"Pull request #214","url":"https://github.com/Vinosaamaa/chanter/pull/214","kind":"pull-request"},{"label":"PR #214 head commit","url":"https://github.com/Vinosaamaa/chanter/commit/24f1f5303a0d76873fde55b10d31898fe84eec61","kind":"commit"},{"label":"PR #214 merge commit","url":"https://github.com/Vinosaamaa/chanter/commit/b63d2530ed6d32098506cce8265ed431405ea5b4","kind":"commit"},{"label":"PR #214 CI run","url":"https://github.com/Vinosaamaa/chanter/actions/runs/29490769920","kind":"run"},{"label":"Issue #188 change log at merge","url":"https://github.com/Vinosaamaa/chanter/blob/b63d2530ed6d32098506cce8265ed431405ea5b4/docs/operations/issue-188-change-log.md","kind":"documentation"}]
verification: {"state":"verified","evidenceRefs":["issue:188","pull-request:214","head-commit:24f1f5303a0d76873fde55b10d31898fe84eec61","merge-commit:b63d2530ed6d32098506cce8265ed431405ea5b4","run:29490769920","documentation:issue-188-change-log"]}
visibility: public-safe
publicationEligibility: eligible
issue: 188
pr: 214
release: null
run: "29490769920"
---
# Service identity hardening across Chanter boundaries

## Context

The codebase review behind issue #188 found that several service endpoints and the realtime handshake could accept a caller-supplied user header before establishing trustworthy provenance. That made the gateway's identity decision insufficient as a security boundary: a direct request to a service could present an identity without independently proving it.

## Boundary change

PR #214 introduced a shared request-identity decision and applied it across the application services. Public HTTP paths now accept either a validated bearer token or a configured internal-service credential paired with a user identity. A user header by itself is rejected, and a bearer token plus a conflicting user header is rejected.

The gateway preserves the validated bearer token when a realtime connection supplies it through the supported query parameter. Realtime WebSocket authentication no longer treats a user header as primary proof. Inter-service HTTP adapters that call public service paths now carry the internal-service proof together with the user identity.

## Verification

The reviewed change added focused coverage for header-only rejection, valid internal-service requests, bearer-token requests, identity conflicts, invalid credentials, unauthenticated requests, and realtime token authentication. The exact PR head completed its backend and frontend CI jobs, and the merge commit preserves the change log describing the affected filters, adapters, and tests.

## Trade-offs and residual boundary

The change tightened an existing identity path without replacing service boundaries or adding a persistence model. Possession of the shared internal-service credential remained a privileged trust boundary; later infrastructure work still needed to reduce direct service exposure and narrow credential blast radius.

## Public-safety boundary

This record contains only reviewed system behavior and immutable public evidence. Contributor contact metadata present in source history is not reproduced. The required PR link remains because the repository owner separately authorized this bounded residual-link publication. A public-safe canonical record and a source carrying unnecessary metadata are treated as different risk surfaces.

## Outcome

Chanter moved from gateway-only identity trust toward service-enforced provenance across HTTP and realtime seams. This retrospective does not establish a public deployment or launch; the verified product state remained a strong local beta.
