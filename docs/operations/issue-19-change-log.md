# Issue 19 Change Log: Answer A Grounded Support Question

Date: 2026-06-23  
Branch: `feature/19-answer-grounded-support-question`  
Issue: `#19 Slice: Answer A Grounded Support Question`

## Acceptance Criteria Covered

- Learner invokes assistant in a granted Course Channel (`POST .../assistant-answer`).
- Assistant uses only approved, granted Course Resources (keyword grounding over downloaded text).
- Response and audit records are persisted in `agent-service`.
- Low-confidence path returns handoff messaging and `AI_LOW_CONFIDENCE` status.
- Tests cover grounded success, handoff, grant denial, and missing install.

## 1. Community Service — Channel Context

- Extended `support-question-access` with `studyServerId` for agent orchestration.

## 2. Message Service — Support Question Lifecycle

- Added `AI_ANSWERED` and `AI_LOW_CONFIDENCE` statuses.
- `GET /api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}` for single-question lookup.
- `PATCH .../status` for assistant outcome updates (sender-only, from `UNANSWERED`).

## 3. Agent Service — Grounded Answers

- Flyway `V2__create_study_assistant_answer_tables.sql` for answers, source citations, and audit records.
- `KeywordGroundingEngine` matches question terms against approved resource text (MVP, not an LLM).
- `POST /api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer`.
- HTTP clients for community channel access, message-service questions, and media resource downloads.
- `GroundedSupportQuestionSmokeTest` (4 cases).

## 4. Gateway And Frontend Demo

- Gateway routes `.../assistant-answer` to agent-service (`order: -2`).
- Frontend demo: post Support Question → **Ask AI Assistant (#19)** → show answer and source citations.

## Verification

- `mvn -pl community-service,message-service,agent-service verify`
- `npm run lint && npm run build`

## Deferred

- Real caller identity remains `TODO(#auth)` / issue #30.
- Approved FAQ grounding deferred to #20.
- TA Queue handoff UI deferred to #21 (low-confidence message only in this slice).
- LLM/runtime integration deferred to a later slice; keyword grounding is intentional for #19.
