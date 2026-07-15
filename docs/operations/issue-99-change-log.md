# Issue #99 — MCP tool bridge for course-grounded assistant tools

## Summary

Embedded Study Assistant tool registry with grant checks and an MCP-compatible internal HTTP surface.

### Tools

| Tool | Purpose | Grant gate |
|------|---------|------------|
| `list_granted_resources` | AI-approved resources granted to the assistant for a course | `COURSE_RESOURCE` ∩ catalog |
| `fetch_resource_chunk` | Fetch one chunk by `resourceId`+`chunkIndex` or `chunkId` | Resource must be granted |
| `search_course_faq` | Search approved FAQs for the course | Course or resource grant; FAQ API enforces enrollment |

### Internal APIs

- `GET /api/v1/internal/assistant-tools` — list tool schemas
- `POST /api/v1/internal/assistant-tools/invoke` — invoke a tool with studyServer/course/viewer scope
- `POST /api/v1/internal/assistant-tools/mcp` — JSON-RPC subset (`tools/list`, `tools/call`)

All require `X-Chanter-Internal-Service-Token`.

### Local demo

```bash
# after make product-up
./scripts/check-assistant-tools.sh
```

To invoke a tool after seeding a demo Study Server, pass the install IDs from `make product-demo-seed` output into `/invoke`.

## Tests

```bash
cd backend && mvn -pl agent-service -am test -Dtest=InternalAssistantToolsSmokeTest
```
