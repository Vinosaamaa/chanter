# Issue 248: implementation and verification

This slice adds a selectable provider runtime, durable token budget accounting, current-evidence checks, and real provider streaming. It leaves source-only as the free default. It does not close #248: grounded tutor synthesis, semantic safety/quality evaluation and #247 retrieval remain open. Native subscriptions are tracked in #316.

## Implementation

- Agent configuration validates per-selection OpenAI, native Anthropic, xAI, compatible and local Ollama contracts, credentials, endpoints, model families and limits. Embeddings remain independent.
- The answer API accepts modelId/answerMode, exposes a current-access catalog, reserves a maximum token budget before generation, and rechecks evidence before publishing chunks. Unavailable explanation mode is explicit.
- Migration V7 creates the durable generation usage ledger. Measurements and unknowns are distinct; missing receipts remain charged. Instructor metrics expose aggregate usage and unknown cost without learner prompts.
- Provider adapters parse native JSON/SSE/NDJSON, propagate cancellation through stalled body reads, enforce terminal states and bounded output, normalize cache/reasoning usage, and redact errors. Generation retries are zero to avoid ambiguous duplicate spend.
- The old duplicate RestClient generation implementation was removed. LlmChatClient retains its original request/response constructors for existing consumers.
- The gateway adds only the exact assistant-models route. No frontend changes or paid provider provisioning are included.

## TDD evidence

Focused red-to-green slices reproduced missing provider configuration, native Anthropic and xAI wire mismatches, stalled stream deadline handling, concurrent quota overspend, fabricated source references, revoked evidence, ignored modelId at the answer endpoint, absent usage aggregates, missing Ollama completion output bounds, late Anthropic output after terminal state, invalid inclusive token totals, and false no-provider-use audit on failed attempts.

The whole endpoint fixture selects local Ollama, emits validated SSE chunks, persists measured usage of 120 tokens, returns instructor-only aggregates, then denies answer reload after resource approval removal. It is an HTTP fixture with the test database and service clients, not a live model or full multi-service retrieval demonstration.

Run affected verification with:

```sh
mvn -s backend/.mvn/settings.xml -f backend/pom.xml -pl agent-service,gateway-service -am verify
```

The committed protocol corpus is backend/agent-service/src/test/resources/evaluations/ai-runtime-v1.json. All five cases passed. Final local verification passed 113 tests: common 13, agent-service 64, gateway-service 36, with zero failures/errors. Hosted-head receipts belong to PR #317. No provider key, paid API call, model download, or native subscription session was used. Frontend and Docker product verification are not claimed by these backend fixtures.

## Operations

Default deployment makes no generation call. Supply an explicit operator catalog and Course export approvals before offering hosted choices; check account-specific spend controls outside Chanter. The API catalog is the UI source of truth, not a hardcoded model list. A configured client is not a successful readiness probe.

Migration rollback is to disable generation and preserve the usage ledger, rather than deleting accounting. A timed-out request may have consumed upstream usage; it retains its full reservation when the provider returns no receipt. A saved handoff is an answer to the current support question; the learner can ask an Instructor or TA rather than incurring an automatic retry.
