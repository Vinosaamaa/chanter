# Issue #188 (SEC-01) Change Log: Service-Level Identity

## Summary

Enforces that public `/api/v1/**` endpoints (except `/api/v1/internal/**`) accept identity from
one of two sources only:

1. Valid Bearer JWT (gateway / browser path), OR
2. `X-Chanter-Internal-Service-Token` matching the service's configured value, **plus** `X-User-Id`

`X-User-Id` alone is now rejected everywhere. If both JWT and `X-User-Id` are present they must
match.

---

## A. AuthenticatedUserFilter — all services

All six existing filters (community, media, notification, search, message, agent) were rewritten
to delegate to `RequestIdentity.requireUserId(...)`. A new filter was created for analytics-service.

Each filter:
- Accepts JWT (Authorization: Bearer) or service-token + X-User-Id
- Wraps the request so `getHeader("X-User-Id")` returns the resolved UUID for downstream handlers
- Rejects with 401 on any other combination
- Skips `/api/v1/internal/` paths where applicable

## B. JwtTokenService bean + YAML config

Added `@Bean JwtTokenService` to existing config classes:
`CommunityServiceConfig`, `AgentServiceConfig`, `MediaServiceConfig`, `MessageServiceConfig`,
`NotificationServiceConfig`, `SearchServiceConfig`, `AnalyticsServiceConfig`.

Each `application.yml` gained:
```yaml
chanter:
  jwt:
    secret: ${CHANTER_JWT_SECRET}
    access-token-ttl: ${CHANTER_JWT_ACCESS_TTL:15m}
  internal-service-token: ${CHANTER_INTERNAL_SERVICE_TOKEN}
```

Each `application-test.yml` gained:
```yaml
chanter:
  jwt:
    secret: chanter-test-jwt-secret-32bytes-min!!
    access-token-ttl: 15m
  internal-service-token: test-internal-service-token-for-<service>
```

## C. RealtimeWebSocketHandler.authenticate

Removed the `X-User-Id`-first branch. Authentication now requires either:
- `Authorization: Bearer <jwt>` (WebSocket upgrade header), OR
- `?access_token=<jwt>` query param

If `X-User-Id` header is present alongside a JWT, they must match or the connection is dropped.

## D. Gateway JwtAuthenticationGlobalFilter

When identity is resolved from `?access_token=<token>`, the gateway now also sets
`Authorization: Bearer <token>` on the mutated downstream request (in addition to `X-User-Id`).
This allows downstream services to validate the JWT directly via their own filter.

## E. Inter-service Http*Client classes

All clients that send `X-User-Id` to peer public `/api/v1/**` endpoints now also send
`X-Chanter-Internal-Service-Token`. Updated clients:

**realtime-service:** HttpChannelMessageClient, HttpDirectMessageClient, HttpSocialFriendsClient
**agent-service:** HttpApprovedFaqClient, HttpCourseResourceCatalogClient, HttpCourseResourceContentClient,
  HttpStudyAssistantGrantCandidatesClient, HttpSupportQuestionClient,
  HttpSupportQuestionChannelAccessClient, HttpStudyServerSaasPlanClient
**analytics-service:** HttpAgentServiceClient, HttpCommunityServiceClient, HttpMessageServiceClient
**search-service:** HttpCommunityNavigationClient, HttpMediaCatalogClient
**media-service:** HttpCourseResourceAccessClient
**message-service:** HttpChannelMessageAccessClient, HttpCohortTaQueueAccessClient,
  HttpCoMembershipClient, HttpCourseChannelAccessClient, HttpCourseResourceAccessClient

Not changed (calls internal endpoints already using service token):
- HttpDmCallMediaTokenClient (realtime) — already sends INTERNAL_SERVICE_TOKEN
- HttpResourceIngestionClient (media) — calls /api/v1/internal/* path
- HttpNotificationClient (community, message) — calls /api/v1/internal/* path
- HttpAuthUserDirectoryClient (community) — calls auth-service internal path

## F. Test Helpers

- `community-service/.../AuthenticatedTestSupport.asUser` — now sets both USER_ID and INTERNAL_SERVICE_TOKEN
- `message-service/.../AuthenticatedTestSupport.asUser` — same
- New `agent-service/.../AuthenticatedTestSupport` class added

All test files that set `AuthHeaders.USER_ID` directly now also set `AuthHeaders.INTERNAL_SERVICE_TOKEN`
(notification, media, search, analytics, agent test files).

## G. New Tests

- `community-service/AuthenticatedUserFilterSmokeTest` — covers: header-only → 401; service token + header → 200; JWT alone → 200; spoofed header+JWT → 401; wrong token → 401; no auth → 401; internal path passthrough
- `realtime-service/RealtimeWebSocketAuthSmokeTest` — covers: header-only WS → rejected; JWT via query param → accepted

## Non-functional impacts

- All services now require `CHANTER_JWT_SECRET` and `CHANTER_INTERNAL_SERVICE_TOKEN` at startup.
- Internal endpoints (`/api/v1/internal/**`) are not affected.
- Realtime HTTP REST endpoints (`/api/v1/direct-message-calls/**`) are handled by `AuthenticatedUserWebFilter` (unchanged, JWT-only).
