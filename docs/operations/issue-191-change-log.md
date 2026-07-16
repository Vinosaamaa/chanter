# Issue #191 Change Log — SEC-11: WebSocket Auth via Sec-WebSocket-Protocol

## Problem

Frontend clients (`realtime-client.ts`, `social-realtime-client.ts`) were placing the JWT in the WebSocket upgrade URL as `?access_token=<token>`. Because the full URL is recorded in server/proxy access logs and browser network-tab history, tokens were leaking.

## Solution

Replace query-param token transport with the `Sec-WebSocket-Protocol` mechanism:

- **Client** sends: `new WebSocket(url, ['chanter-jwt', accessToken])` — no query token in URL.
- **Server** negotiates `chanter-jwt` back, satisfying the browser's subprotocol handshake requirement.
- **Gateway** resolves identity from the `Sec-WebSocket-Protocol` header for `/api/v1/realtime/**` requests, injects `Authorization` and `X-User-Id` on the downstream request.

## Files Changed

### Frontend
| File | Change |
|------|--------|
| `frontend/src/features/realtime/realtime-client.ts` | Removed `?access_token=` from URL; pass `['chanter-jwt', accessToken]` as WebSocket subprotocols. |
| `frontend/src/features/friends/social-realtime-client.ts` | Same. |

### Backend — Realtime Service
| File | Change |
|------|--------|
| `backend/realtime-service/src/main/java/com/chanter/realtime/websocket/RealtimeWebSocketHandler.java` | `authenticate()` now prefers `Authorization` header, then parses `Sec-WebSocket-Protocol` header (`chanter-jwt, <token>` or `chanter-jwt.<token>`). Removed `access_token` query param fallback. Added `getSubProtocols()` override returning `["chanter-jwt"]` so Spring WebFlux negotiates the protocol correctly (prevents browser close). Removed dead `queryParam()` helper. |

### Backend — Gateway Service
| File | Change |
|------|--------|
| `backend/gateway-service/src/main/java/com/chanter/gateway/security/JwtAuthenticationGlobalFilter.java` | `resolveIdentity()` now accepts `Sec-WebSocket-Protocol` (`chanter-jwt, <token>`) for `/api/v1/realtime/**` instead of `access_token` query param. Removed URI sanitisation for `access_token`. Added `extractTokenFromSubprotocols()` static helper. |

### Tests
| File | Change |
|------|--------|
| `backend/realtime-service/src/test/java/com/chanter/realtime/api/RealtimeWebSocketAuthSmokeTest.java` | Renamed `validJwtViaQueryParamIsAccepted` → `validJwtViaSubprotocolIsAccepted`; uses `withJwtSubprotocol()` wrapper pattern. |
| `backend/realtime-service/src/test/java/com/chanter/realtime/api/RealtimeWebSocketSmokeTest.java` | All `websocketUri(token)` calls updated to `websocketUri()` + `withJwtSubprotocol(token, handler)`. |
| `backend/realtime-service/src/test/java/com/chanter/realtime/api/SocialRealtimeWebSocketSmokeTest.java` | Same. |
| `backend/realtime-service/src/test/java/com/chanter/realtime/api/DirectMessageCallSignalingSmokeTest.java` | Same. |
| `backend/gateway-service/src/test/java/com/chanter/gateway/security/JwtAuthenticationGlobalFilterPublicAuthPathsTest.java` | Added `realtimePathWithSubprotocolTokenIsAccepted`, `realtimePathWithoutTokenIsRejected`, `extractTokenFromSubprotocolsTwoValueForm`, `extractTokenFromSubprotocolsDotForm`, `extractTokenFromSubprotocolsReturnsNullWhenAbsent`. |

## Subprotocol Negotiation Detail

`ReactorNettyWebSocketClient` calls `handler.getSubProtocols()` to build the `Sec-WebSocket-Protocol` request header. The test helper `withJwtSubprotocol(token, delegate)` returns a `WebSocketHandler` whose `getSubProtocols()` yields `["chanter-jwt", token]`, causing the client to send `Sec-WebSocket-Protocol: chanter-jwt, <token>`. The server-side `extractTokenFromSubprotocols()` handles both the two-value comma-delimited form and the dot-concatenated form `chanter-jwt.<token>`.

## Security Impact

- JWT tokens no longer appear in WebSocket upgrade URLs or server access logs.
- Token is transmitted only in the `Sec-WebSocket-Protocol` header, which is part of the HTTP upgrade handshake and is not logged by typical reverse proxies.
