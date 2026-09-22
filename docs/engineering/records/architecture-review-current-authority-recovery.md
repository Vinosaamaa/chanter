---
schemaVersion: 1
id: architecture-review-current-authority-recovery
revision: 1
type: architecture-review
status: proposed
title: Current deletion authority before application recovery
repository: chanter
capabilityIds: []
createdAt: 2026-09-22
reconstructed: false
confidence: high
unknowns: ["Actual seven-source PostgreSQL recovery depends on accepted lifecycle handlers", "Provider freshness protection, retained object consistency and original-writer fencing remain unverified"]
modules: ["deployment"]
interfaces: ["terminal-journal-replica", "private-lifecycle-recovery"]
seams: ["verified-external-prefix-before-checkpoint", "source-owned-recovery-receipt"]
adapters: ["restic", "docker-compose-exec"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Encrypted deletion journal replica", "Fail-closed current authority recovery"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #332","url":"https://github.com/Vinosaamaa/chanter/issues/332","kind":"issue"}]
verification: {"state":"verified","evidenceRefs":["scripts/deploy/terminal-journal-replica.test.mjs", "scripts/deploy/terminal-journal-storage.test.mjs", "scripts/deploy/terminal-journal-recovery.test.mjs", "docs/operations/issue-332-change-log.md"]}
visibility: public-safe
publicationEligibility: eligible
issue: 332
pr: null
release: null
run: null
---
# Current deletion authority before application recovery

A database backup can restore rows whose deletion happened afterward. Recovery therefore needs the current independently retained terminal journal, followed by each source's committed access fences and credential invalidation. A checkpoint restored from the old database provides a minimum prefix, never proof of current authority.

The replica reuses accepted checksum-pinned restic encryption and stores bounded immutable pages plus a manifest. It validates the actual Java journal digest format, complete contiguous history and all required lower bounds. Full stored-page verification occurs before manifest publication and again after publication before auth acknowledgement. Checkpoint identity remains stable on retry. Forks, gaps, malformed newest manifests, capacity overflow and unavailable storage fail closed without falling back to an older prefix.

The production host reaches private endpoints through a fixed localhost JDK helper inside the existing owning service container. There is no new listener or network service. The helper reads its internal credential from the service environment, accepts bounded stdin and has an independent process deadline. The host validates protocol receipts after successful execution; it never treats Docker success as a completed deletion.

The bounded recovery module requires all seven source receipts and durable auth/agent invalidation receipts at one externally verified authority. Pending cleanup and preservation remain explicit. The result always retains public isolation. Hash-chain integrity cannot prove freshness after a complete repository rollback, and rereading a head does not fence original writers. Provider protection, operator authority, actual source effects and whole-application/object validation remain release gates.

Local tests use real encrypted restic storage and the actual Java digest vector. Injected source tests establish ordering and refusal only. Hosted transport and dual-architecture storage tests are added for independent validation; complete PostgreSQL source effects await accepted #251. This record does not assert a public deployment, provider drill, recovery-time guarantee or completed #332 acceptance.
