---
schemaVersion: 1
id: architecture-review-ai-answer-controls
revision: 1
type: architecture-review
status: proposed
title: "Authorized AI answer choices and interrupted-stream recovery"
repository: chanter
capabilityIds: ["chanter-ai-study-assistant-runtime"]
createdAt: 2026-09-12
reconstructed: false
confidence: high
unknowns: ["Hosted browser and exact-head CI checks are pending on the initial candidate.", "Live provider accounts, semantic explanations and production deployment are unverified."]
modules: ["frontend-questions", "frontend-course-workspace"]
interfaces: ["authenticated-assistant-model-catalog", "assistant-answer-sse"]
seams: ["stream-draft-to-saved-answer", "provider-attempt-to-source-recovery"]
adapters: []
relatedRecords: ["architecture-review-chanter-ai-provider-runtime@1"]
decisions: []
incidents: []
features: []
capabilities: ["Authorized answer selection", "Saved answer provenance", "Interrupted answer recovery"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"AI answer UI integration", "url":"https://github.com/Vinosaamaa/chanter/issues/321", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:stream-assistant-contract", "test:use-questions-channel", "test:AssistantAnswerControls", "test:CourseQuestionsPage"]}
visibility: public-safe
publicationEligibility: eligible
issue: 321
pr: null
release: null
run: null
---
# Authorized AI answer choices and interrupted-stream recovery

The browser previously invoked the server default without exposing the authorized model catalog, ignored structured stream errors, and accepted stream EOF without a completion event. Learners could not distinguish source retrieval from quotation extraction or identify the actual model behind a saved answer.

The Questions workspace now consumes the authenticated catalog and sends explicit model/mode query parameters only on invocation. It explains the selected capability and provider billing, disables unavailable explanations and incompatible source/quotation combinations, and labels saved answers from the persisted audit. The existing backend remains the authority for authorization, billing and replay.

Only a completed SSE frame commits an answer in the browser. EOF and errors discard drafts; transport readers are released. Request ownership prevents late callbacks from a superseded question or channel from changing the current view. After a possibly billable request, the current workspace permits only explicit source-only recovery. This conservative UI rule does not replace the durable server ledger across reloads and tabs.

The implementation adds a compact controls component within the existing reading pane. Existing design tokens and buttons keep the new CSS within the unchanged bundle budget. Local tests reproduce the prior failure modes and pass after the changes. Hosted fixture images prove responsive rendering separately from the real seeded source-retrieval and persisted-reload browser test. Exact-head review, hosted checks and release evidence are recorded in the owning receipt and issue before delivery.
