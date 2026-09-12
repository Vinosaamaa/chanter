# Secure browser sessions and transactional email

Owning issue: [#242](https://github.com/Vinosaamaa/chanter/issues/242). Parent launch program: [#107](https://github.com/Vinosaamaa/chanter/issues/107).

Status: implementation in progress. This document defines the intended contract; the issue change log records what has actually passed verification.

## Problem and customer outcome

The previous browser keeps an access token in local storage and loses its in-memory refresh token on reload. A stolen access token remains accessible to JavaScript, and a returning customer cannot reliably renew a session. The only email sender discards verification and recovery messages, so production account recovery has no delivery path.

A customer must be able to register, receive and use a verification message, sign in, reload, continue after access-token expiry, inspect active sessions, revoke another session, sign out, and reset a forgotten password. Every step must run through the same application endpoints used by production.

## Boundaries and ownership

| Component | Owns | Must not own |
|---|---|---|
| Browser auth store | Current access token and user in memory, initialization state, account-change generation | Renewable credential or persisted bearer token |
| Browser session coordinator | Serialized cookie refresh, sign-in/sign-out ordering, cross-tab invalidation | Authorization decisions |
| Gateway | Public/protected route classification, bearer validation, explicit browser-origin allowlist | Refresh-token state or email contents |
| Auth service | Users, password verification, session families, rotation, verification/reset tokens, email queue | Course roles or other services' databases |
| SMTP transport | Delivery of queued transactional messages to the configured provider | Account creation or token lifetime decisions |
| Mailpit | Local development and test inbox | Production customer email |

Existing service boundaries remain. This slice does not replace the backend architecture or add an auth proxy service.

## Browser session contract

The server returns an access token and user in JSON after successful authentication. It sends the refresh credential only through the `chanter_refresh` cookie. The cookie is `HttpOnly`, `Secure`, `SameSite=Strict`, has path `/api/v1/auth`, and has no Domain attribute. Production runs behind HTTPS on the application's own origin. Any HTTP development exception must be explicit and restricted to loopback.

The browser keeps the access token in memory. On startup it tries cookie refresh before deciding whether a protected route needs sign-in. API calls capture the current account generation. A response started under an earlier account must not update the new account's cache or retry using its credentials. An expired access token triggers one coordinated refresh and one retry, not an unlimited loop.

Browser tabs serialize cookie-changing operations with Web Locks where supported. Shared browser state carries only an opaque account-change marker, never a token. Sign-out and account switching invalidate pending work in every tab. The UI must distinguish initialization, a transient renewal failure, and an anonymous session without displaying another account's content.

## Refresh-token lifecycle

Each login creates a durable session family with creation, last-use, expiry, and a bounded device description. Refresh credentials are random; only their hashes are stored. A successful refresh consumes one token and creates its successor under a database lock. Rotation keeps the family identity and its fixed expiry.

A token that has already been consumed is evidence of reuse. Auth revokes that family and commits the revocation before returning an error. It must not throw inside a transaction in a way that rolls back the security change. Concurrent legitimate browser refreshes must be serialized by the client rather than weakening reuse detection.

Session listing and revocation require the authenticated user's identity. A session identifier belonging to another account must not expose metadata or permit revocation. Revoking the current session clears its cookie and the browser's in-memory state. Password reset revokes the account's refresh sessions.

Previously issued access tokens remain valid until their existing short expiry, currently 15 minutes. Immediate invalidation at every service and realtime connection would require a separate cross-service revocation contract. Session controls must describe this bound accurately.

## Request origin and cross-site request forgery

Cross-site request forgery means another website causes the browser to submit an authenticated request using its cookies. Cookie-mutating browser endpoints require `X-Chanter-CSRF: 1` and an allowed Origin. The custom header forces a browser preflight for cross-origin calls; the origin allowlist and rejection of simple cross-site requests provide the other half of the defense. SameSite cookies add protection but do not replace these checks.

Apply the policy to sign-in and OAuth exchange as well as refresh and logout. Reject malformed, unexpected, and mismatched origins. API scripts must explicitly supply the documented origin and header. Never trust a caller-supplied forwarded host as an origin allowlist.

## Transactional email

Auth writes the email-token hash and an outgoing message in the same database transaction. A background worker delivers committed messages over authenticated SMTP with required transport encryption in production. A provider failure does not discard the message or roll back an already committed account.

The queue stores the delivery payload only while it is needed. Payloads contain one-time links and must be treated as credentials. Logs and metrics contain bounded delivery state and opaque identifiers, never recipients, message bodies, tokens, or provider credentials. Successful, expired, and exhausted entries must clear their sensitive payloads according to the implemented retention rule.

Retries are bounded and end before the associated link expires. The retry delay starts at 30 seconds and increases to a 15-minute cap. Concurrent workers must not send the same claimed row; an abandoned transaction must release its claim. SMTP acknowledgement proves that the next server accepted the message, not that a customer received it. A crash after SMTP acceptance and before the database commit can produce a duplicate message. The one-time token remains the authority, so duplicate mail does not grant extra access. Production acceptance also needs a real provider inbox check.

The worker clears sent and expired payloads and retains only non-sensitive delivery metadata for seven days. Its health indicator reports an overdue queue or retry backlog using counts, without connecting to a provider merely to calculate health. Pending queue bodies remain sensitive auth database data and require the same access and backup protections as other credentials.

`CHANTER_PUBLIC_BASE_URL` controls links. Production requires a valid HTTPS origin without credentials, query, or fragment. `CHANTER_EMAIL_FROM` must be a provider-verified sender. Local Mailpit requires an explicit local-sink setting and loopback browser origin. The application must fail startup for a discarded-mail provider or incomplete production delivery configuration.

## Migration and rollout

The V3 migration adds session-family state and preserves the refresh-token lineage required for reuse checks. It invalidates pre-migration refresh credentials because they were JavaScript-readable and have no trusted family lineage. Existing users must sign in once after this release. The V4 migration adds the transactional email queue.

Deploy schema changes through Flyway with the auth release. Back up auth data first. A rollback must account for new cookies and session state; old code cannot be assumed compatible with newly rotated credentials. Do not drop tables or rewrite applied migrations during rollback. Staging rehearsal and an exact tested release are prerequisites for production.

## Verification matrix

| Risk | Required evidence |
|---|---|
| Renewable credentials exposed to JavaScript | Response JSON, cookie attributes, local/session storage assertions |
| Rotation race or replay accepted | Database-backed concurrent/reuse tests and family-revocation checks |
| Revocation rolled back on error | Separate request after rejected reuse cannot renew the family |
| Cross-site login/refresh/logout | Missing-header, wrong-origin, malformed-origin and allowed-origin tests |
| Cross-account data leakage | Deferred response/refresh tests and two-account browser sequence |
| Broken recovery | Real SMTP sink, browser verification link, reset link, old-session rejection |
| Lost mail on provider failure | Committed queue survives failure, retries, and clears delivered payload |
| Duplicate queue worker claims | Competing row-lock claims, transaction rollback and retry tests |
| Broken production routing | Gateway-backed browser run through the configured public origin |
| Unusable session controls | Keyboard operation, accessible names, desktop/mobile visual inspection |

## Alternatives considered

Persisting refresh tokens in browser storage would improve reload behavior but expose renewable credentials to JavaScript. Keeping access tokens persisted would preserve the current theft window. Both are rejected.

Making every service introspect a central session record would enable immediate access revocation but adds a synchronous auth dependency to all requests. This slice keeps bounded access-token expiry and records that limit.

Sending email inside the account transaction couples customer signup to a provider outage. A database-backed queue avoids that coupling and makes failures retryable. Provider-specific HTTP adapters remain unnecessary while standard SMTP satisfies the configured provider.

## Open launch gates

Live SMTP credentials, sender verification, provider acceptance/delivery, a real HTTPS staging origin, and staging browser receipts remain deployment-owned gates. Local tests and a merged pull request do not satisfy those gates.
