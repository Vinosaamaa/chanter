---
schemaVersion: 1
id: architecture-review-native-subscription-companion
revision: 1
type: architecture-review
status: proposed
title: "Provider-owned authentication and a restricted native study companion"
repository: chanter
capabilityIds: ["chanter-ai-study-assistant-runtime"]
createdAt: 2026-09-12
reconstructed: false
confidence: medium
unknowns: ["Eligible-account subscription inference is unverified.", "Pairing, backend capability acceptance, signing, and operational retention remain incomplete.", "Native isolation is exercised on Windows CLI 0.153.4 only."]
modules: ["native-companion"]
interfaces: ["companion/src/codex-app-server.mjs", "companion/src/codex-launch.mjs"]
seams: ["native-ui-to-provider-managed-auth", "restricted-stdio-to-provider-runtime", "backend-evidence-to-local-capability"]
adapters: ["companion/src/codex-app-server.mjs"]
relatedRecords: ["architecture-review-chanter-ai-provider-runtime@1"]
decisions: []
incidents: []
features: []
capabilities: ["Provider-managed native authentication", "Sanitized account and model discovery", "Bounded private stdio", "Native isolation canary fixtures"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Native subscription companion issue", "url":"https://github.com/Vinosaamaa/chanter/issues/316", "kind":"issue"}, {"label":"Provider runtime issue", "url":"https://github.com/Vinosaamaa/chanter/issues/248", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:companion/tests/codex-account.test.mjs", "test:companion/tests/codex-app-server.test.mjs", "test:companion/tests/codex-launch.test.mjs", "test:companion/tests/native-codex.test.mjs"]}
visibility: public-safe
publicationEligibility: eligible
issue: 316
pr: null
release: null
run: null
---
# Provider-owned authentication and a restricted native study companion

Consumer subscriptions are not generic API credentials. The native companion keeps provider authentication in an unmodified user-owned Codex process and separates its provider home from personal Codex integrations. The browser cannot proxy arbitrary Codex methods, credentials, executables, endpoints, or filesystem paths. Device authentication remains a provider-owned native user action.

The first boundary uses explicit private stdio methods, sanitized account/model/limit summaries, bounded frames and request timeouts, and stable error codes. API-key accounts are rejected and external-token login/refresh methods are unavailable. Authentication provenance requires the private method allowlist and provider-owned store; the account category alone does not establish it. Missing usage stays unknown; neither a plan label nor a client usage claim authorizes a billable request.

Native tests exercise CLI 0.153.4 with a synthetic loopback provider, an empty environment selection, no loaded instruction sources, disabled integrations, and a restricted permission profile. The provider still advertises a fixed skills namespace. Both skill catalogs are empty, planted host skills stay undiscovered, forged absolute-path package reads fail, and unadvertised shell calls fail without disclosing canaries. This is a finite native compatibility result, not a claim of zero tools or universal process isolation.

Production evidence release requires an independently authenticated native pairing and a backend-issued single-use request capability binding the current user/session, provider/model, evidence hashes, bounds, and expiry. Current authorization, export approval, reservation, and evidence validation must be repeated at the appropriate backend boundaries. A possible provider attempt retains the existing one-attempt-per-question rule. Client-reported usage is provenance, not billing authority.

The initial 21-test checkpoint establishes the protocol/configuration slice only. No real provider inference, eligible-account login, signed installer, pairing endpoint, or backend capability integration is claimed. Ephemeral threads and upstream `store=false` were observed, but provider operational databases still exist and need a retention audit. The remaining delivery and security gates are explicit in the issue design and system review.
