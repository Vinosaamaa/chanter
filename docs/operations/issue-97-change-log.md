# Issue #97 — LLM provider adapters

## Summary
Adds Ollama and OpenAI-compatible chat adapters behind `CHANTER_LLM_ENABLED` (default false). Health probe: `GET /api/v1/internal/llm/health`. Smoke: `scripts/check-llm-provider.sh`.

## Verify
```bash
cd backend && mvn -pl agent-service -am test
./scripts/check-llm-provider.sh
```
