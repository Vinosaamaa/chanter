---
schemaVersion: 1
id: architecture-review-free-beta-entitlements
revision: 1
type: architecture-review
status: proposed
title: "Operator-controlled free beta and truthful lifetime usage"
repository: chanter
capabilityIds: ["chanter-ai-study-assistant-runtime"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Hosted browser and final exact-head checks are pending on the initial candidate.", "Paid-provider behavior and production release proof remain outside the free-beta slice."]
modules: ["community-entitlements", "assistant-quota", "frontend-usage"]
interfaces: ["study-server-saas-plan", "instructor-dashboard"]
seams: ["legacy-plan-to-operator-entitlement", "actual-count-to-usage-display"]
adapters: []
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Operator-owned beta limits", "Truthful lifetime usage"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Truthful beta and billing", "url":"https://github.com/Vinosaamaa/chanter/issues/250", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:SaasPlanSmokeTest", "test:FreeBetaConfigurationTest", "test:UsageSettingsPage", "test:quota-message"]}
visibility: public-safe
publicationEligibility: eligible
issue: 250
pr: null
release: null
run: null
---
# Operator-controlled free beta and truthful lifetime usage

Owners could previously change their own quota by patching a simulated plan tier. The interface described a monthly allowance even though the audit counter covered all saved runs. No payment event authorized the increase.

The community service now derives the effective entitlement from bounded operator configuration and rejects the former mutation route. Historical tier rows are retained but cannot raise the active limit. Unsupported paid mode fails startup. Existing provider-token and private-storage limits remain separate.

The owner Usage page displays the actual lifetime count and remaining runs without checkout, card, invoice, upgrade or reset claims. Loading and failed requests do not display fabricated zero usage. The old billing route redirects, and other plan-changing UI has been removed.

HTTP and configuration tests establish quota-tampering rejection and policy precedence. UI tests distinguish real counts from missing data; real-service browser and responsive fixture checks provide separate persistence and presentation evidence. Paid billing remains a future requirement, so this change does not close issue #250.
