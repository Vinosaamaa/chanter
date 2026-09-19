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
now reject literal/encoded semicolons before policy matching. Native subscription
request/result routes now explicitly consume AI admission and concurrency limits.
Local real gateway/auth regressions pass. Resize-created widgets have a generation
check so a removed widget cannot restore a stale token.

Final exact-head hosted checks, full review and screenshot inspection remain
required. No provider protection or public launch is asserted here.
