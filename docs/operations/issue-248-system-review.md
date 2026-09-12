# Issue 248: system review

Reviewed scope: agent provider/runtime configuration, HTTP adapters, request cancellation, usage reservation/settlement, evidence publication, instructor aggregates, and the exact gateway catalog route. No UI or deployment is represented as complete.

## Boundaries reviewed

The free source path performs no generation and no token reservation. Explicit hosted selection requires the authenticated Course to be approved for that provider configuration. Provider-controlled errors are replaced with fixed outcomes. Current channel/assistant/resource/FAQ authorization is rechecked at the content boundary and on answer reload. Instructor usage authorization runs before querying ledger aggregates.

The installation row serializes reservations; a reservation commits before network work. Unknown and stale requests remain budgeted, and repeated settled receipts do not replace prior measurements. Cancellation closes active network reads. A child generation deadline cannot outlive the outer request, and a generation timeout leaves time to return a clear handoff. One generation step and zero automatic retries avoid hidden cost amplification.

The question's claim survives settlement, answer-save failure, process loss and daily budget reset. Only locally proven pre-provider failure releases it. This closes the settlement/persistence gap without holding database locks during network work. A lost answer cannot be regenerated through a paid provider; authorized source-only recovery remains available and preserves the unknown usage receipt. Staff evidence reads use installation grants plus their own current resource access, not learner enrollment.

Native Anthropic and xAI protocols are separate. xAI Responses accounts for reasoning in output bounds. Generic compatible adapters require operator conformance checks. No configured provider is reported as reachable merely because construction succeeded.

## Remaining release work

- #247 must prove real ingestion/retrieval lifecycle behavior, scoped embeddings, source replacement/deletion, broader document formats, and cross-service authorization under concurrent changes. Current tests use local fixtures and H2; PostgreSQL and full service integration must be verified before production use.
- Quotation matching is not semantic groundedness or teaching quality. Grounded-explanation remains unavailable. Meaningful explanations need a representative versioned evaluation and explicit release thresholds; the five protocol cases do not satisfy that requirement.
- Sensitive-data patterns and instruction boundaries are incomplete safety defenses. A production safety/refusal assessment remains required for the selected model and Course corpus.
- Cancellation stops this client's I/O; it cannot guarantee that an upstream provider immediately stops billing. Unknown usage is conservatively reserved. Accurate cost depends on configured pricing and a matching resolved model.
- Download and evidence loops stop before starting the next call after cancellation; an in-flight synchronous content request may finish its configured read timeout (10 seconds by default). Other legacy retrieval/access client internals remain outside the provider transport's active cancellation hooks. Distributed authorization cannot atomically lock another service's policy; publication rechecks reduce but cannot eliminate that interval.
- A container timeout or broken connection can close SSE without a terminal error frame. Spring completes the emitter before its timeout callback. Frontend consumers must reject EOF without authoritative complete and discard partial text/citations; the coordinated #248 frontend integration owns that consumer repair after #254.
- Native subscription support still requires #316 installation, pairing, process isolation, entitlement and end-to-end evidence. Browser/API credentials are not substituted for native subscriptions.

No confirmed security defect is knowingly deferred in the implemented boundary. The limitations above prevent claiming full #248 completion or public launch readiness.
