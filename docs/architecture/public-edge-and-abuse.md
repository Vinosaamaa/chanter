# Public edge and abuse controls

Issue #253 starts from accepted deployment `a51b1b0`. The free deployment can
serve directly through Caddy; Cloudflare is optional when an owned zone exists.
The launch configuration serves directly through Caddy. Proxying an owned zone
through Cloudflare requires a separately reviewed configuration and provider proof.
This document records the implemented contract; final hosted and provider verification are pending.

## Client identity

Caddy is the only published HTTP entry point. It removes caller-supplied identity
and internal-service headers. The gateway independently removes every forwarded
or Cloudflare identity header, plus caller-supplied user/service identities. It
uses the socket peer unless that peer is an explicitly configured proxy address.
Only a single literal IP from that trusted peer may replace the socket identity.
No DNS lookup, arbitrary private-network trust or first-value comma parsing is
allowed. Spring's automatic forwarded-address transformation stays disabled.
JWT validation subsequently sets the authenticated user; rate-limit keys never
use a caller's `X-User-Id`.

Production gives Caddy a fixed address on a dedicated edge network. The gateway
trusts that exact address, not the shared application subnet. Dynamic addresses
are allocated from a separate range so LiveKit cannot take Caddy's identity.
A future Cloudflare mode must verify origin authentication and published proxy
network ranges at Caddy; direct mode ignores Cloudflare headers. Only the configured public base
URL determines downstream forwarding scheme/host. Actual firewall and origin
bypass tests remain required when the provider is configured.

## Shared request budgets

Use the existing private Redis instance, not a new service. Atomic expiring
counters apply shared IP and authenticated-user budgets by operation class.
Tenant-path partitions supplement those budgets as user/tenant pairs; an
unverified tenant hint never grants access or consumes another user's allowance.
Backend membership, AI reservations, email budgets and storage quotas remain
authoritative for their own data and costs. Gateway limits provide an earlier
admission boundary, not replacement authorization.

Keys contain keyed hashes rather than raw addresses or credentials. Limits have
bounded lifetimes and stable HTTP 429/Retry-After responses. Redis failure must
not silently remove protection from sensitive writes. Health probes remain
available; logout has a bounded recovery path and cannot be blocked indefinitely
by an unrelated budget. Browser preflight performs no expensive provider work.
Reject excessive payloads before buffering, bound upload/download concurrency,
and preserve the application's explicit streaming/cancellation contract.

## Bot proof and browser behavior

Optional Turnstile proof is validated server-side for the configured hostname
and action. Signup/recovery retain a stricter, accessible email-verification
alternative when a challenge cannot be completed or the provider is unavailable.
Neither provider outage nor the absence of a JavaScript widget may permanently
lock an existing user out of login, logout or recovery. This alternative remains
subject to distributed admission and the existing durable email sending budget.
No paid bot-management product is required.

Security headers and CORS must be explicit. CSP must cover the actual voice,
resource, optional bot-proof and native-companion connections without wildcard
credentialed origins. Error responses expose retry guidance, not credential,
request-body or private provider data. Correlation identifiers are generated or
validated locally; arbitrary forwarded identifiers are not audit authority.

## Verification and rollout

Start with regressions for forged forwarding/user/internal-service headers,
trusted/untrusted peers, malformed IPs and the actual gateway's outgoing headers.
Then verify shared limits with two independent gateway instances against real
Redis, including simultaneous requests, expiry and outages. Verify body and
concurrency bounds, browser security, graceful reconnects and optional bot-proof
failure behavior. Production image scans, full backend/frontend tests, browser
checks for affected controls and full CodeAnt review gate the final PR.

The implementation must update the operator runbook and generated runtime
configuration together. Actual cloud firewall, DNSSEC, public TLS, media reachability
and any enabled Cloudflare policies require separate provider evidence before
public launch. No account or paid service is provisioned by this source change.

References: [Spring Gateway header filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/httpheadersfilters.html),
[Cloudflare headers](https://developers.cloudflare.com/fundamentals/reference/http-headers/),
[Turnstile validation](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/).

## Implemented request bounds

The gateway gives each IP a shared 1,200-request minute budget and each verified
user a shared 600-request minute budget. Operation limits are specified in
`RequestBudgetPolicy`; tenant hints cannot replace those global allowances.
Registration uses 12/IP/minute, AI invocation uses 20/user/minute, uploads use
10/user/minute, and search uses 60/user/minute. Saved-answer reads, model catalogs,
feedback and file deletion do not consume inference or upload capacity. Native
subscription request/result routes use the same AI admission class.

Ordinary bodies are limited to 256 KiB, multipart upload bodies to 11 MiB, and the
outer Caddy API limit to 12 MB. Caddy bounds headers to 16 KB, header reads to ten
seconds and body reads to sixty seconds. The gateway also bounds chunked bodies
before forwarding any bytes. Active per-instance work is limited to 32 ordinary
requests, two uploads, eight downloads, six inference requests and 128 realtime
sockets, with narrower per-user limits. Signup/login allow eight simultaneous
requests per address for shared networks and neutral duplicate signup handling.
Ordinary upstream responses time out after two minutes, downloads after five;
only actual GET WebSocket upgrades on realtime routes retain the service's
heartbeat lifecycle. Capacity rejection returns 503 with Retry-After.

A one-second Redis deadline deliberately fails ordinary admission closed. A
late Lua execution can conservatively consume a counter, but never forwards or
retries the rejected operation. The recovery exception remains capped at sixty
requests per process per minute. Real Redis tests cover two gateways, atomic
concurrency, expiry, spoofed identities, tenant rotation and an unavailable store.

Matrix-parameter path suffixes, literal or encoded semicolons, are rejected
before route/policy matching and independently at auth. This prevents Spring's
route normalization from selecting a less protected budget or bypassing proof.
Every gateway request receives a fresh correlation ID forwarded to the service
and returned to the browser; a caller-supplied ID cannot replace it.

## Verification control design

Keep the accepted Chanter auth composition, type scale and blue action color.
Use white #ffffff, text #192c46, secondary text #596a80, blue #2458d3 and the
existing pale page background. The control is a left-aligned continuation of the
form, immediately before its primary action, with no separate decorative card.
The text explains the next action; the email alternative has a 44-pixel target
and inherits the auth page's visible keyboard focus. The provider widget uses
compact sizing below 300 pixels of available form width and flexible sizing
above it. Resizing discards the old token and ignores old widget callbacks.

`GET /api/v1/auth/verification-options` exposes only the public site key. The
optional server verifier checks single-use provider success, configured hostname
and action under a three-second request deadline. A missing/expired proof gives
428; a provider failure gives 503. No token or secret is logged or persisted.
Login, logout and already issued email links do not depend on the challenge.

The explicit email alternative sends `X-Chanter-Verification-Method: email` and
reduces signup/recovery IP limits to three/six per minute. It cannot grant an
unverified session: enabling Turnstile requires enforced email verification.
The existing durable sending budget still applies. Each completed form attempt
clears/remounts its challenge; unmounted, resized and replaced widgets cannot
restore a stale proof. Hosted browser fixtures exercise provider failure at
320, 390 and 1280 pixels in Chromium, Firefox and WebKit. They do not establish
live provider acceptance, which needs the configured public hostname/account.
