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
unknowns: ["Eligible-account subscription inference is unverified.", "Production key provisioning/rotation and final claim retention remain incomplete; package is OS-unsigned.", "Native isolation is exercised on Windows CLI 0.153.4 only."]
modules: ["native-companion", "agent-service", "auth-service", "common", "gateway-service", "frontend"]
interfaces: ["companion/src/codex-app-server.mjs", "companion/src/codex-launch.mjs", "companion/src/native-request.mjs", "companion/src/native-server.mjs", "companion/src/native-state.mjs"]
seams: ["native-ui-to-provider-managed-auth", "restricted-stdio-to-provider-runtime", "backend-evidence-to-local-capability"]
adapters: ["companion/src/codex-app-server.mjs"]
relatedRecords: ["architecture-review-chanter-ai-provider-runtime@1"]
decisions: []
incidents: []
features: []
capabilities: ["Provider-managed native authentication", "Sanitized account and model discovery", "Bounded native streaming and cancellation", "Native isolation canary fixtures", "Signed loopback request validation", "Protected pairing and durable replay rejection"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Native subscription companion issue", "url":"https://github.com/Vinosaamaa/chanter/issues/316", "kind":"issue"}, {"label":"Provider runtime issue", "url":"https://github.com/Vinosaamaa/chanter/issues/248", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:companion/tests/codex-account.test.mjs", "test:companion/tests/codex-app-server.test.mjs", "test:companion/tests/codex-launch.test.mjs", "test:companion/tests/codex-study-turn.test.mjs", "test:companion/tests/native-codex.test.mjs", "test:companion/tests/native-request.test.mjs", "test:companion/tests/native-server.test.mjs", "test:companion/tests/native-state.test.mjs"]}
visibility: public-safe
publicationEligibility: eligible
issue: 316
pr: 324
release: null
run: null
---
# Provider-owned authentication and a restricted native study companion

Consumer subscriptions are not generic API credentials. The native companion keeps provider authentication in an unmodified user-owned Codex process and separates its provider home from personal Codex integrations. The browser cannot proxy arbitrary Codex methods, credentials, executables, endpoints, or filesystem paths. Device authentication remains a provider-owned native user action.

The first boundary uses explicit private stdio methods, sanitized account/model/limit summaries, bounded frames and request timeouts, and stable error codes. API-key accounts are rejected and external-token login/refresh methods are unavailable. Authentication provenance requires the private method allowlist and provider-owned store; the account category alone does not establish it. Missing usage stays unknown; neither a plan label nor a client usage claim authorizes a billable request.

Native tests exercise CLI 0.153.4 with a synthetic loopback provider, an empty environment selection, no loaded instruction sources, disabled integrations, and a restricted permission profile. The provider still advertises a fixed skills namespace. Both skill catalogs are empty, planted host skills stay undiscovered, forged absolute-path package reads fail, and unadvertised shell calls fail without disclosing canaries. This is a finite native compatibility result, not a claim of zero tools or universal process isolation.

Production evidence release requires an independently authenticated native pairing and a backend-issued single-use request capability binding the current user/session, provider/model, evidence hashes, bounds, and expiry. Current authorization, export approval, reservation, and evidence validation must be repeated at the appropriate backend boundaries. A possible provider attempt retains the existing one-attempt-per-question rule. Client-reported usage is provenance, not billing authority.

The resumed boundary adds a real loopback HTTP listener with exact Host/Origin checks and a non-simple authenticated request. Pairing is a native action without an HTTP route. A pinned deployment key verifies short-lived Ed25519 tickets binding identity, session, installation, question, provider/model, mode, prompt/evidence hashes, and bounds. Native approval is bounded by ticket expiry and repeated pairing validation precedes the durable claim. SQLite commits that claim before transport, so uncertain completion cannot produce an automatic retry. Windows filesystem tests reject broadened state permissions; installation secrets and consumed identifiers remain native. The database stores no provider credentials or course text.

An unmodified native Codex fixture runs through signed pairing, the HTTP listener, durable consumption, and bounded private stdio. It uses synthetic signing keys and a loopback model provider, with no login, copied credential, external inference, or subscription usage. This is protocol/security evidence only. Eligible-account flow and production provisioning remain incomplete; the integrated issuer and browser flow are described below.

The unsigned developer source installer supplies a visible native terminal with random expiring approval challenges, sanitized status, explicit provider-owned device login/logout, and serialized start/stop/restart. Redirected input cannot grant approval. Native inference checks subscription authentication, advertised model, and known unexhausted limits. Setup checks do not imply an authenticated account or running service.

Ephemeral threads and upstream `store=false` still create operational state. Supported SQLite/log placement now confines it to a private per-operation directory removed only after the owned provider exits. A persistent provider-owned authentication home remains separate. Orphan recovery requires both recorded processes to be absent; ambiguous/live/linked state blocks execution. Tests preserve a synthetic provider-owned marker while proving no operational SQLite remains in that home. Provider-side retention and final durable-claim retention remain separate gates. The remaining delivery and security gates are explicit in the issue design and system review.

The integrated backend uses durable browser session identity and private authenticated introspection for live revocation. Native issuance reuses current retrieval/resource checks and the atomic generation reservation. Result acceptance claims immutable session/installation/question/model scope once, repeats current evidence authorization, validates quotations, and stores client usage only as untrusted provenance. Temporary evidence expires; UNKNOWN accounting retains the conservative reservation. The desktop UI binds terminal pairing and per-question export consent to explicit native approval, intersects current models, cancels on account changes, and shows only server-validated saved answers. Mobile/API-only use remains independent. Tested source archives use checksums and free GitHub attestations rather than purchased OS certificates.
