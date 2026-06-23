# Issue 18 Change Log: Install The AI Study Assistant

Date: 2026-06-22  
Branch: `feature/18-install-ai-study-assistant`  
Issue: `#18 Slice: Install The AI Study Assistant`

## Acceptance Criteria Covered

- Instructor reviews and confirms assistant grants before install (HITL preview + confirm POST).
- Users see assistant presence and allowed scope (`GET` install/presence).
- Backend stores install and grant records.
- Tests cover grant boundaries.

## 1. Community Service — Grant Candidates

- Added `GET /api/v1/study-servers/{studyServerId}/study-assistant-grant-candidates?userId=`.
- Returns Study Server channels, Courses, Cohorts, and Course Channels when caller is Study Server Owner or Instructor on any Course in that server.
- Added `GET /api/v1/study-servers/{studyServerId}/study-assistant-viewer-scope?userId=` for enrollment-aware presence filtering.
- Added `StudyAssistantGrantCandidatesSmokeTest`.

## 2. Agent Service — Install And Presence

- Bootstrapped `agent-service` on port `8085` with Flyway `V1__create_study_assistant_tables.sql`.
- `GET .../study-assistant/install-preview` merges community grant candidates with AI-approved Course Resources from media-service.
- `POST .../study-assistant/install` validates confirmed grants are a subset of preview candidates; one install per Study Server.
- `GET .../study-assistant?viewerUserId=` returns installed flag and grants (Instructors see all; enrolled learners see enrollment intersection).
- HTTP clients for community and media with timeouts; test doubles back smoke tests.
- Added `StudyAssistantInstallSmokeTest` (grant boundary, conflict, presence).

## 3. Gateway, Infra, And Frontend Demo

- Gateway routes `/api/v1/study-servers/*/study-assistant/**` to agent-service (`order: -1`).
- PostgreSQL init adds `chanter_agent` database.
- `make backend-agent` target and `.env.example` entries for port `8085`.
- Frontend demo panel: preview install, confirm (HITL), show presence for Instructor and Learner.

## Verification

- `mvn -pl community-service,agent-service verify`
- `npm run lint && npm run build`

## Deferred

- Real caller identity remains `TODO(#auth)` / issue #30.
- Assistant runtime, channel bindings, and Q&A behavior deferred to later slices.
