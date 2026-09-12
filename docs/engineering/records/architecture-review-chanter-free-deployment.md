---
schemaVersion: 1
id: architecture-review-chanter-free-deployment
revision: 1
type: architecture-review
status: accepted
title: Reproducible deployment within the free resource budget
repository: chanter
capabilityIds: ["production-deployment"]
createdAt: 2026-09-12
reconstructed: false
confidence: high
unknowns: ["External account and A1 capacity availability", "Owned public DNS and SMTP sending identity", "Image scan and container staging results", "Measured production load and rollback evidence", "Durable off-host recovery proof under issue 244"]
modules: ["release-package", "host-deployment", "production-edge"]
interfaces: ["immutable-release-manifest", "private-runtime-environment", "schema-compatibility-epoch"]
seams: ["service-owned-database-migrations", "secure-browser-sessions", "durable-storage-recovery"]
adapters: ["github-actions", "docker-compose", "caddy", "oracle-always-free"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Immutable architecture-specific release packages", "One active environment inside a bounded VM", "Guarded deployment and compatible application rollback"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Deployment issue #243","url":"https://github.com/Vinosaamaa/chanter/issues/243","kind":"issue"},{"label":"Secure browser sessions #242","url":"https://github.com/Vinosaamaa/chanter/issues/242","kind":"issue"},{"label":"Oracle Always Free resource limits","url":"https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm","kind":"documentation"}]
verification: {"state":"verified","evidenceRefs":["scripts/deploy/host.test.mjs","scripts/deploy/release.test.mjs","scripts/deploy/probe.test.mjs","docs/operations/issue-243-change-log.md"]}
visibility: public-safe
publicationEligibility: eligible
issue: 243
pr: 315
release: null
run: null
---
# Reproducible deployment within the free resource budget

## Context and decision

Chanter already has ten Java services and a React client. The owner requires free resources and has no infrastructure accounts. Converting the working product into a different architecture merely to fit a 512 MB free container would add application risk without proving a usable deployment. The accepted package keeps current service boundaries and uses one fixed 2 OCPU/12 GB Always Free A1 VM, with service-owned PostgreSQL databases, Redis, LiveKit and Caddy. Current provider capacity and account eligibility remain external unknowns.

The running containers have a combined 7,808 MiB memory cap. Staging and production have separate state, secrets and volumes, but only one can reserve the VM at a time. Continuous staging runs on disposable standard runners for the public repository. This replaces the original managed-database and simultaneous staging/production assumptions with explicit downtime and a single host failure boundary. It does not promise high availability or measured load capacity.

## Release and trust boundaries

Pinned multi-architecture upstream digests feed architecture-specific image builds. A reviewed GitHub release delivers an offline image archive, checksum, exact image-ID manifest and host scripts. Only a manual merged-main workflow with successful exact-commit CI can write the draft release. Pull-request builds have read-only permissions. Every image is scanned and started in ephemeral staging before publication; no production credentials enter CI.

Private per-service environment files are generated on the host. Compose's raw file format preserves provider-password punctuation. Initialization refuses partial or existing state, validation rejects missing values, and the gateway receives no database or SMTP credential. The release manifest and Docker image IDs are public configuration; runtime files and host state are private operator data.

Caddy serves frontend and API on one HTTPS origin. It strips client identity/internal-service headers, controls cache and security headers, and supplies the exact origin required by #242. Actual DNS, certificate issuance, sender identity, external media reachability and any optional Cloudflare edge controls require provider evidence before public enrollment.

## Migration and failure boundary

Deployment stops ingress and application processes, starts persistence, runs each owned database's Flyway migrations in a one-shot process, starts applications, then checks public HTTPS health before recording success. PostgreSQL-specific migrations are included; the image smoke checks their unique indexes and repeats migration execution against durable Flyway history. Normal application containers disable migration-on-start.

Automatic recovery and explicit rollback only reuse an older binary when its reviewed schema epoch and persistence images are compatible. Neither path deletes data or reverses schema. A failed public health check stops ingress; a compatible previous release is restarted and checked, while incompatible changes require fix-forward or independently reviewed restore. Host locks and a durable active-environment marker also cover a failed first installation.

## Evidence and remaining proof

Local regression tests cover immutable manifests, resource and port boundaries, secret isolation, initialization and validation failures, migration ordering, rollback compatibility, failed-health recovery, partial-install stopping and the actual Java readiness helper. OpenTofu validates the pinned OCI configuration, Docker Compose validates rendered configuration, and Caddy validates its routing policy. Shell parsing and migration-helper compilation pass.

There is no Docker engine or public VM in the implementation environment, so image builds/scans, full container staging and real-host deployment/rollback are unverified until their dedicated runs complete. The operator runbook lists those gates and the account checkpoint. Storage/restore work under #244 and provider-agnostic AI under #248 remain separate scope; MinIO, Redpanda and paid inference are not deployed by this package.
