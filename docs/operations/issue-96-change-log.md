# Issue #96 — RAG grounding engine

## Summary

Ask AI now uses `#95` vector retrieval by default (`chanter.grounding.engine=rag`). Citations include resource titles and chunk character offsets. Weak scores fall back to approved FAQs, then LOW confidence + TA handoff. Set `CHANTER_GROUNDING_ENGINE=keyword` to force the legacy keyword engine.

## Changes
- `RagGroundingEngine` (default)
- `KeywordGroundingEngine` behind `chanter.grounding.engine=keyword`
- `GroundedSupportQuestionService` retrieves grant-scoped chunks before answering
- Empty vector store falls back to keyword over downloaded text/FAQs

## Verify
```bash
cd backend && mvn -pl agent-service -am test
```
