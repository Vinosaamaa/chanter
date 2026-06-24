# agent-service

AI Study Assistant install, grants, and presence for Study Servers.

Local port: `8085`.

## APIs

- `GET /api/v1/study-servers/{studyServerId}/study-assistant/install-preview?instructorUserId=` — HITL preview of grant candidates and AI-approved Course Resources
- `POST /api/v1/study-servers/{studyServerId}/study-assistant/install` — confirm install with explicit grants (one assistant per Study Server)
- `GET /api/v1/study-servers/{studyServerId}/study-assistant?viewerUserId=` — installed flag and grants visible to the viewer
- `POST /api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer` — grounded answer or low-confidence handoff for an unanswered Support Question

## Dependencies

- `community-service` for grant candidates, viewer enrollment scope, and `#questions` channel access
- `message-service` for Support Question lookup and status updates
- `media-service` for AI-approved Course Resources per course and resource content downloads

Requires PostgreSQL database `chanter_agent` (see `infra/postgres/init/01-databases.sql`).

Caller identity uses query/body params for the local demo harness (`TODO(#auth)` — real auth deferred to issue #30).
