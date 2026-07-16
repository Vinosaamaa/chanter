# agent-service

AI Study Assistant install, grants, and presence for Study Servers.

Local port: `8085`.

## APIs

- `GET /api/v1/study-servers/{studyServerId}/study-assistant/install-preview` — HITL preview of grant candidates and AI-approved Course Resources (actor from `X-User-Id`)
- `POST /api/v1/study-servers/{studyServerId}/study-assistant/install` — confirm install with explicit grants (one assistant per Study Server; actor from `X-User-Id`)
- `GET /api/v1/study-servers/{studyServerId}/study-assistant` — installed flag and grants visible to the viewer (actor from `X-User-Id`)
- `GET /api/v1/study-servers/{studyServerId}/ai-usage-metrics` — SaaS AI usage for instructor dashboard (actor from `X-User-Id`)
- `POST /api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer` — grounded answer or low-confidence handoff for an unanswered Support Question
- `POST /api/v1/course-channels/{channelId}/support-questions/{supportQuestionId}/assistant-answer/stream` — SSE token stream then final answer JSON

## LLM orchestration + MCP

Ask AI can refine RAG answers through optional LLM adapters (`CHANTER_LLM_ENABLED`, Ollama / OpenAI-compatible). Tool calling for orchestration uses an **embedded MCP-compatible tool registry** that never bypasses Study Assistant grants.

| Tool | What it does |
|------|----------------|
| `list_granted_resources` | Lists AI-approved resources granted to the assistant for a course |
| `fetch_resource_chunk` | Returns one chunk; rejects out-of-grant resources |
| `search_course_faq` | Searches approved course FAQs within enrollment scope |

Internal endpoints (service token required):

```bash
# List schemas
curl -s http://localhost:8085/api/v1/internal/assistant-tools \
  -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN"

# Invoke (example)
curl -s http://localhost:8085/api/v1/internal/assistant-tools/invoke \
  -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tool": "list_granted_resources",
    "studyServerId": "...",
    "courseId": "...",
    "viewerUserId": "...",
    "arguments": {}
  }'

# MCP JSON-RPC subset
curl -s http://localhost:8085/api/v1/internal/assistant-tools/mcp \
  -H "X-Chanter-Internal-Service-Token: $CHANTER_INTERNAL_SERVICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Local smoke: `./scripts/check-assistant-tools.sh`. See `docs/operations/issue-99-change-log.md`.

## Dependencies

- `community-service` for grant candidates, viewer enrollment scope, and `#questions` channel access
- `message-service` for Support Question lookup and status updates
- `media-service` for AI-approved Course Resources per course and resource content downloads

Requires PostgreSQL database `chanter_agent` (see `infra/postgres/init/01-databases.sql`).

Caller identity uses query/body params for the local demo harness (`TODO(#auth)` — real auth deferred to issue #30).
