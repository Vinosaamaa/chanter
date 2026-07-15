# Issue #98 — Agent runtime orchestration

## Summary
Optional LLM refinement of RAG answers when `CHANTER_LLM_ENABLED=true`. Audit rows record `llm_provider` / `llm_model` / `llm_used`. SSE streaming endpoint:

`POST /api/v1/course-channels/{channelId}/support-questions/{id}/assistant-answer/stream`

Emits `token` events then a `complete` event with the persisted answer JSON. Quota still enforced via existing 429 path.

## Streaming note
When LLM is disabled, the endpoint still streams the RAG answer body in chunks so the UI path works locally without Ollama.
