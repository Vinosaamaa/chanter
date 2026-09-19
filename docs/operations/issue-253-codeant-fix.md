# Issue 253 review fixes

Full CodeAnt review of the pushed admission checkpoint was inspected alongside
local reproductions. AI route classification now distinguishes actual inference
from saved-answer/catalog reads and feedback; upload capacity applies to upload
POSTs instead of all resource mutations. A mapped-IPv6 regression confirms the
already canonicalized socket/configured address path. The one-second Redis
deadline remains intentional bounded, fail-closed behavior: a late counter
increment is conservative and never retries an upstream request.

Hosted product tests found that the four-request per-address active cap rejected
otherwise neutral simultaneous registration. Auth/registration now allow eight
within the unchanged 32-request instance cap and shared minute limits. A focused
concurrency test confirms excess rejection and capacity release after cancellation.

Both native staging architectures reproduced a dynamic network allocation taking
Caddy's reserved address. Dynamic edge addresses now use the upper /29 of the
/28, separate from the gateway/proxy static addresses. Native staging must rerun
at the new head before acceptance.

Independent review reproduced a matrix-parameter suffix bypassing the optional
auth challenge while Spring matched the protected route. Both gateway and auth
now reject matrix parameters and encoded paths before policy matching, including
encoded letters in route names that Spring otherwise decodes. Query/body text is
unaffected. Native subscription
request/result routes now explicitly consume AI admission and concurrency limits.
Local real gateway/auth regressions pass. Resize-created widgets have a generation
check so a removed widget cannot restore a stale token.

Final exact-head hosted checks, full review and screenshot inspection remain
required. No provider protection or public launch is asserted here.

The final integrated run also passed all product journeys. Its Redis AI test still
used the old assistant-install path after classification was corrected; it now
uses the real question-answer invocation while rotating courses and gateways.
The browser fixture now declares disabled challenge configuration by default,
while provider-outage tests override it explicitly. Recovery tests wait for the
new page heading before filling Email, preventing input into the previous route.
The control reuses accepted auth styles to keep the existing CSS budget unchanged.

Full review also questioned mapped-IPv6 canonicalization and provider error
classification. Actual Java21 tests confirm InetAddress.getByAddress collapses
mapped addresses, including a forced Inet6Address socket fixture. No alternate
normalization is needed. Siteverify's authoritative success:false response rejects
proof; non-200 or malformed upstream responses are unavailable because they do
not establish a token rejection. Returning503 preserves fail-closed behavior and
the explicit email alternative without blaming the user for a provider/configuration
failure. The fixed endpoint and bounded deadline remain unchanged.

Coordinated249 routing adds only the specified reports/platform-admin paths to
auth. All platform-admin traffic and moderation mutations use SENSITIVE budgets;
actual operator role, step-up, evidence and enforcement remain249 responsibilities.
Internal moderation paths have no public route. Gateway HTTP tests verify this
boundary independently of the future service handlers.
