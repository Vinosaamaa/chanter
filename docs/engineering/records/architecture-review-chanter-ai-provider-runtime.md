---
schemaVersion: 1
id: architecture-review-chanter-ai-provider-runtime
revision: 1
type: architecture-review
status: proposed
title: "Bounded selectable AI providers with current-evidence checks"
repository: chanter
capabilityIds: ["chanter-ai-study-assistant-runtime"]
createdAt: 2026-09-12
reconstructed: false
confidence: high
unknowns: ["Live provider account access and billing are unverified.", "Semantic tutor quality and real retrieval remain dependent on issue 247 and evaluations.", "Native subscription delivery is tracked separately in issue 316."]
modules: ["backend-agent-service", "backend-gateway-service"]
interfaces: ["backend/agent-service/src/main/java/com/chanter/agent/application/LlmChatClient.java", "backend/agent-service/src/main/java/com/chanter/agent/api/AiModelCatalogController.java", "backend/agent-service/src/main/java/com/chanter/agent/api/GroundedSupportQuestionController.java"]
seams: ["authorized-evidence-to-provider", "reservation-to-network-generation", "provider-stream-to-validated-answer"]
adapters: ["backend/agent-service/src/main/java/com/chanter/agent/infra/AnthropicLlmChatClient.java", "backend/agent-service/src/main/java/com/chanter/agent/infra/ResponsesLlmChatClient.java", "backend/agent-service/src/main/java/com/chanter/agent/infra/XaiLlmChatClient.java", "backend/agent-service/src/main/java/com/chanter/agent/infra/OllamaLlmChatClient.java"]
relatedRecords: ["feature-retrospective-chanter-ai-study-assistant-runtime@1"]
decisions: []
incidents: []
features: []
capabilities: ["Explicit source and extraction modes", "Provider-specific streaming", "Durable token reservations", "Current authorization and citation validation", "Measured and unknown usage aggregates"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"AI production runtime issue", "url":"https://github.com/Vinosaamaa/chanter/issues/248", "kind":"issue"}, {"label":"Native subscription companion", "url":"https://github.com/Vinosaamaa/chanter/issues/316", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:NativeProviderContractTest", "test:ResponsesProviderContractTest", "test:AiGenerationLedgerTest", "test:GroundedSupportQuestionSmokeTest", "test:AiModelRouteTest"]}
visibility: public-safe
publicationEligibility: eligible
issue: 248
pr: 317
release: null
run: null
---
# Bounded selectable AI providers with current-evidence checks

## Problem and decision

A global provider string could select incompatible protocols, ignore measured usage, and stream a precomputed answer while presenting a general model capability. The runtime now exposes explicit model and answer-mode choices, dispatches native protocols, and commits a maximum token reservation before network generation. The default approved-source path remains free of provider requests.

OpenAI and xAI use Responses; Anthropic uses Messages; local Ollama uses chat NDJSON. Generic compatible access is a separate operator-verified Chat Completions contract. A shared bounded HTTP transport owns deadlines, cancellation and safe failures, while adapters own their protocol and token semantics. No speculative agent control plane or provider tool execution is introduced.

## Evidence and privacy boundary

Current membership, assistant grants, Course approval and source text are checked before provider transmission and before publishing evidence. Saved answers are rechecked on read. An exact quote proves textual provenance, not semantic relevance or instructional quality. Source-only and quotation extraction are explicit; grounded study explanations remain unavailable pending retrieval and semantic evaluation. This record does not close the complete AI product requirement.

Generation receipts contain opaque identifiers, normalized usage, safe outcomes, latency and versioned cost estimates. They do not duplicate learner questions or provider bodies. Unknown usage retains the full reservation instead of appearing as zero. Instructor aggregates require instructor authorization and distinguish unknown cost from known estimates.

## Verification and limits

Vertical red-to-green tests covered configuration, native wire contracts, cancellation after response headers, atomic concurrent reservations, missing usage, unsupported quotations, revoked sources, model selection through the answer endpoint, and instructor usage access. The five-case versioned protocol corpus is separate from model-quality evaluation. Local affected-module Maven verification and the final hosted head are recorded in the pull request receipt.

Provider readiness, live account entitlement, PostgreSQL behavior, real ingestion/retrieval and intelligent tutor quality are not established by HTTP fixtures and H2. The implementation/system review in docs/operations/issue-248-system-review.md records those release boundaries. Native subscription use has its own delivery issue because origin pairing, process isolation, authentication ownership and result provenance require independent security evidence.
