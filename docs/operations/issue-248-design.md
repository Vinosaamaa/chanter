# Issue 248: provider runtime design

Status: implemented runtime foundation; intelligent tutor release remains blocked by #247 retrieval and semantic evaluations. Native subscription integration is tracked separately in #316. No paid provider calls or model downloads were used to build this slice.

## Request and capability contract

`GET /api/v1/course-channels/{channelId}/assistant-models` requires authenticated channel access and current assistant grants. It returns `defaultModelId`, `models`, and `answerModes`. Each model has `id`, `label`, `provider`, `model`, `mode` (sources, local, or api), and `billing`. No endpoint or credential is returned. The gateway has an exact route predicate for this resource.

Existing answer POST and `/stream` endpoints accept optional `modelId` and `answerMode` query parameters. Existing clients may omit both. Modes are separate from how a provider is accessed:

| Answer mode | Behavior | Availability |
|---|---|---|
| source-only | Existing approved-source answer; no generation request or token reservation | Default, always available when the assistant is granted |
| quoted-evidence | Model selects exact relevant quotations from authorized excerpts | Requires a configured model and Course export approval for hosted providers |
| grounded-explanation | Study explanation with supported claims, citations, examples, and explicit uncertainty | Unavailable; requests fail with 409 until retrieval and semantic evaluation are verified |

An explicit unavailable model is denied before provider work. An unavailable deployment default falls back to source-only. Selecting source-only with a configured model still performs no generation. An existing persisted answer remains the answer to that question; changing selectors does not regenerate or recharge it.

SSE emits status, validated token chunks during provider generation, and complete with the authoritative persisted answer. Clients replace partial text with complete; they discard partial text on error. A failure after a partial quote can end in a low-confidence handoff. Error events contain safe code/status/message values; they never include provider bodies. Client disconnect or timeout cancels the active provider HTTP request/body read. There are no automatic generation retries: ambiguous network failure could already have consumed provider usage.

## Configuration

The Spring configuration namespace is `chanter.llm`. `default-model-id` defaults to source-only and `daily-token-limit` defaults to 100000 per Study Server per UTC day. `models` is a map keyed by stable operator-selected IDs. Each entry configures label, provider, model, optional base-url, a secret-injected api-key for hosted access, max-input-tokens (8192), max-output-tokens (1024), timeout (30s), allowed-course-ids, and optional versioned per-million-token prices. Models are configured explicitly; legacy enabled=true without a catalog fails startup. Merely supplying a catalog enables its choices regardless of the old enabled flag.

No hosted model is preconfigured. Hosted choices require explicit Course IDs, including custom compatible endpoints. Ollama is restricted to local/service HTTP hosts and rejects cloud model names; operators must also run Ollama with `OLLAMA_NO_CLOUD=1`. Local operation incurs compute cost but no provider API charge. Configure only models whose provider capabilities and license have been checked. Configuration validation is not proof that an account has access, a model is installed, or the provider is reachable.

OpenAI uses native Responses; Anthropic uses native Messages; xAI uses Responses because its max_output_tokens includes reasoning. Generic compatible uses the OpenAI Chat Completions contract and must be verified by the operator to implement its output bound, streaming, and usage semantics. This generic contract is not an override for xAI's different Chat usage semantics. Ollama uses native chat JSON/NDJSON. Embedding configuration remains under chanter.embeddings, independent of generation; changing a chat model does not re-embed resources.

Input uses a conservative UTF-8 byte bound plus framing allowance. Generation has one step, no provider tools, a configured 1–120s deadline, maximum 8192 output tokens, bounded response/line sizes, and cancellation hooks. HTTP redirects are disabled. These are request controls, not proof against a nonconforming provider billing beyond its advertised bound.

## Authorization and accounting

Before sending content, each request rechecks channel membership, assistant installation/grants, Course resource approval, and current source content. Vector candidate IDs are intersected with current approved Course IDs. Rechecks also happen before each released evidence chunk, final publication, and reading a saved answer. Current text must still contain the cited excerpt; stale content or removed approval fails closed. FAQ evidence is checked against the current approved FAQ set. The existing text-only retrieval seam and cross-service race windows remain #247 work.

A committed database reservation locks the assistant installation before generation and charges the full input/output budget. Concurrent requests cannot both spend the remaining daily budget. Settlement records measured usage once, or retains the reservation if usage is unknown. Stale pending reservations remain charged. Raw provider text, questions, excerpts and credentials are not copied into the generation ledger. Its records contain selection/provider/model, opaque request ID, normalized token measurements, latency, safe outcome, and optional versioned estimated cost. Existing answer storage still contains the authorized learner question and answer under its existing access policy.

Unknown usage is null, never fabricated zero. Input totals include cache reads/writes; output totals include reported reasoning. Missing cost metadata remains unknown. Instructor usage aggregates distinguish unknown usage/cost and pending/error counts. Estimated cost is neither a provider invoice nor subscription entitlement. Subscription or API overage is never automatically enabled.

## Grounded explanation release boundary

Quoted evidence can verify textual provenance; it cannot establish semantic relevance, teach concepts, or prove a synthesized claim. The future explanation contract must represent claims with supporting source IDs/excerpts, explanatory text, uncertainty and handoff. Paraphrases and examples are allowed only after a versioned evaluation demonstrates support, scope, usefulness, and safety; explanatory words are not required to be quotations. Do not advertise this runtime foundation as a finished intelligent tutor.

Protocol evaluation ai-runtime-v1 covers exact source, invented claim, wrong source, explicit uncertainty and injected instruction fixtures. It does not measure model groundedness. Release requires authorized retrieval lifecycle tests from #247 and a reviewed, representative corpus with semantic claim support, citation correctness, unsupported-question refusal, cross-course denial, useful explanation quality, and provider-specific latency/usage budgets. Safety regexes are defense in depth, not comprehensive harmful-content or personal-data classifiers.

## Supported subscription companion (#316)

The access direction is a user-owned local companion using a provider-owned unmodified native client. Codex App Server owns managed ChatGPT login/refresh and exposes model/account limits; Chanter does not read its auth files. Claude Code keeps all built-in authentication choices and requires applicable Commercial Terms when packaged. Each user authenticates through the provider's own flow. Never pool subscriptions or forward subscription tokens into the hosted API adapters.

The companion reuses model selection, answer modes, evidence IDs, limits, cancellation, and usage semantics. A separate short-lived single-use request capability must bind the user/question/provider/model, approved source hashes, budget and expiry. Exact-origin local pairing, restricted process tools/filesystem, transcript retention, result tamper checks, and current-authorization rechecks are required before delivery. Companion-reported usage is untrusted client evidence, not authoritative billing. #316 owns installation, UI, threat-model tests and a consenting account's native end-to-end proof; this design does not deliver that feature.

## Official provider entitlement evidence (checked 2026-09-12)

- [OpenAI authentication](https://learn.chatgpt.com/docs/auth): native Codex subscription access and Platform API keys are separate access methods; subscription credentials are not generic API keys.
- [Codex App Server](https://learn.chatgpt.com/docs/app-server): managed ChatGPT browser/device-code login keeps token ownership with Codex and exposes account/model/limit information.
- [Anthropic API billing](https://support.claude.com/en/articles/9876003-i-have-a-paid-claude-subscription-pro-max-team-or-enterprise-plans-why-do-i-have-to-pay-separately-to-use-the-claude-api-and-console): direct API billing is separate from consumer plans.
- [Claude Code legal conditions](https://code.claude.com/docs/en/legal-and-compliance): permitted unmodified native-client hosting requires each user's own authentication and forbids credential intermediation.
- [Claude SDK plan update](https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan): the announced June SDK credit change is paused; no entitlement to that withdrawn credit is assumed.
- [xAI FAQ](https://docs.x.ai/grok/faq): API use can participate in weekly subscription usage; applicability and overage are account-dependent and have not been verified for this deployment.
- [xAI Responses contract](https://docs.x.ai/developers/rest-api-reference/inference/responses): bounded output includes reasoning; Chat Completions has different accounting.
- [Ollama FAQ](https://docs.ollama.com/faq): local-only operation requires cloud features to be disabled. No local model installation or cloud entitlement is implied.
