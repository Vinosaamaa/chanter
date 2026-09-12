---
schemaVersion: 1
id: architecture-review-supported-backend-runtime
revision: 1
type: architecture-review
status: proposed
title: Supported backend runtime and complete package security
repository: chanter
capabilityIds: ["reproducible-deployment", "identity-and-recovery"]
createdAt: 2026-09-12
reconstructed: false
confidence: high
unknowns: ["Exact-head hosted checks and review are pending", "Native application and base image scans remain required by issue 243", "Public production deployment is unverified"]
modules: ["backend-services", "auth-service", "gateway-service", "release-tooling"]
interfaces: ["browser-session", "backend-package-security"]
seams: ["framework-dependency-management", "vulnerability-database"]
adapters: ["spring-boot", "spring-cloud-gateway", "spring-security", "trivy"]
relatedRecords: ["architecture-review-secure-browser-sessions@1"]
decisions: []
incidents: []
features: []
capabilities: ["Supported backend dependency baseline", "Full packaged-library security coverage", "Complete long-password verification"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue 319","url":"https://github.com/Vinosaamaa/chanter/issues/319","kind":"issue"}]
verification: {"state":"not-recorded","evidenceRefs":[]}
visibility: public-safe
publicationEligibility: eligible
issue: 319
pr: null
release: null
run: null
---
# Supported backend runtime and complete package security

## System review

The first production image scan found 33 high or critical dependency findings in the existing Spring Boot 3.4.1 application. Existing unit and browser checks did not detect those packaged vulnerabilities. A source-oriented scanner can report no vulnerabilities while missing nested Java libraries entirely, so a scan must establish coverage before its findings are trusted.

## Decision

Adopt Spring Boot 4.0.8 and its compatible Spring Cloud 2025.1.3 train on Java 21. Pin the Tomcat security fix to 11.0.25, Nimbus JOSE JWT to 10.9.1 and test-only H2 to 2.5.250. Preserve existing HTTP JSON types through Boot's official Jackson 2 compatibility module and explicit mapper configuration. Use the relocated Gateway route namespace, dedicated Flyway starter, and module-specific test support. The ten existing service boundaries and production database schemas remain unchanged.

The updated BCrypt implementation exposes the existing conflict between the advertised 128-character password and BCrypt's 72-byte input bound. New registrations and resets use Spring Security PBKDF2-HMAC-SHA256 with versioned parameters; existing BCrypt hashes remain readable. The deployment compatibility epoch must advance before these hashes are stored because previous application code cannot decode them.

## Security evidence boundary

Extract each built executable JAR into a fresh ignored directory. Scan actual application resources and every nested library using Trivy rootfs mode. Require package identity coverage for every expected library and verify the executable's hash remains unchanged during scanning. Any missing coverage, scan failure, high/critical advisory or secret finding fails the gate. Publish only sanitized service-level coverage, digests and advisory metadata; raw secret matches and local filesystem paths stay out of artifacts.

## Consequences and verification

The repair retains endpoint behavior while changing framework integration points, so all backend regressions and actual signed-in browser journeys remain merge requirements. Local compatibility tests and scanner coverage regressions are recorded in `docs/operations/issue-319-change-log.md`. The native image scans, migration replay and HTTPS checks in issue 243 remain required before release. A clean Java package scan cannot establish operating-system or public-provider readiness.

Detailed implementation, password recovery limitations, rollout compatibility and upstream references are in `docs/architecture/supported-backend-and-security-scans.md`. This proposed record does not claim hosted, merged-main, release or production completion.
