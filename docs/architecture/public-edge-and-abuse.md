# Public edge and abuse controls

Issue #253 starts from accepted deployment `a51b1b0`. The free deployment can
serve directly through Caddy; Cloudflare is optional when an owned zone exists.
The application must remain secure in either mode. This document records the
implementation contract before code changes; provider verification is pending.

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
trusts that exact address, not the shared application subnet. Cloudflare mode
also verifies origin authentication and the published proxy network ranges at
Caddy; direct mode ignores Cloudflare headers. Only the configured public base
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
