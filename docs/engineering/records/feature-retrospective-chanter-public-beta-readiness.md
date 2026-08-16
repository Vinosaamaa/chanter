---
schemaVersion: 1
id: feature-retrospective-chanter-public-beta-readiness
revision: 1
type: feature-retrospective
status: released
title: "feat(auth): #102 production auth (verify, reset, SSO)"
repository: chanter
capabilityIds: ["chanter-public-beta-readiness"]
createdAt: 2026-07-15
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and workflow logs were not quoted.","A public launch was not evidenced; release truth remains strong local beta.","Sensitive source values and nonessential risky evidence links were omitted."]
modules: ["backend-auth-service","frontend-auth"]
interfaces: ["backend/auth-service/src/main/java/com/chanter/auth/api/AuthController.java","backend/auth-service/src/main/java/com/chanter/auth/api/AuthUserResponse.java","backend/auth-service/src/main/resources/db/migration/V2__production_auth.sql","backend/auth-service/src/test/java/com/chanter/auth/api/ProductionAuthSmokeTest.java","frontend/src/features/auth/auth-api.ts"]
seams: ["browser-to-auth-service","auth-domain-to-postgresql","external-identity-to-local-account","auth-service-to-email-delivery"]
adapters: ["backend/auth-service/src/main/java/com/chanter/auth/application/AuthEmailTokenRepository.java","backend/auth-service/src/main/java/com/chanter/auth/application/OAuthAccountRepository.java","backend/auth-service/src/main/java/com/chanter/auth/infra/JdbcAuthEmailTokenRepository.java","backend/auth-service/src/main/java/com/chanter/auth/infra/JdbcOAuthAccountRepository.java","frontend/src/features/auth/pages/OAuthCallbackPage.tsx"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Configurable account verification","Password recovery","External identity sign-in","Authentication request-rate controls"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Pull request #172","url":"https://github.com/Vinosaamaa/chanter/pull/172","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:172","head-commit:cee0e32c531ba4ab59b89c87ab150d52d2159148","merge-commit:0ef7d09ef3d3ae6884ab6b29931084e7a2690c2d"]}
visibility: public-safe
publicationEligibility: eligible
issue: 102
pr: 172
release: null
run: null
---
# feat(auth): #102 production auth (verify, reset, SSO)

## Context

The local sign-in flow lacked account verification, recovery, and optional external identity integration needed for a credible deployment path. Pull request #172 added those capabilities while leaving them configuration-dependent and preserving the local demonstration mode.

## Delivered boundary

The Auth Service added verification and recovery token persistence, password reset, request-rate controls, and optional external identity account linking. Frontend routes covered verification, forgotten credentials, reset, and the external identity callback. Email delivery remained behind an adapter, allowing the reviewed slice to exercise the flow without claiming a production provider was operating.

Configuration controlled account-verification enforcement and external identity availability. The change hardened application behavior but did not provision provider credentials or a public environment.

## Verification

Backend smoke coverage exercised the new auth flows, and frontend tests and build checks covered the routed user experience. Public-safe logging deliberately excluded message bodies and recipient details.

## Consequences

Authentication gained the state and interfaces required for a deployment-ready account lifecycle. Follow-on work still needed provider-backed delivery, secure durable browser sessions, reproducible infrastructure, and real production validation.

## Historical limits

Sensitive source values and nonessential risky evidence links were omitted. The historical issue title uses “production auth,” but this record does not infer a public beta or deployed identity provider.
