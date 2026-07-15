# Issue #100 — Streaming AI answer UX, citations, Mark helpful

## Summary

Learner Ask AI now consumes the SSE `/assistant-answer/stream` path (tokens → complete). Citation chips appear with the final answer. Learners can **Mark helpful**. Instructors see an AI audit snippet (RAG vs LLM provider/model + source count).

### Backend
- Flyway `V6` helpful feedback table
- `POST .../assistant-answer/helpful`
- `AssistantAnswerResponse` includes `audit`, `helpfulMarked`, `helpfulCount`
- Gateway routes `assistant-answer/**` to agent-service (fixes stream/helpful)

### Frontend
- `streamAssistantAnswer` SSE client
- `useQuestionsChannel` streaming state machine + mark helpful
- `CourseQuestionsPage` streaming affordance, citation chips, helpful button, audit snippet

### Tests
- Hook: stream tokens → complete; mark helpful
- Existing CourseQuestionsPage tests updated for new hook fields
