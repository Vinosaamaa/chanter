# Issue 243 review dispositions

The initial full CodeAnt review completed against `d9d6a1e`. Follow-up full
review remains required at the final integrated head, separately from quality,
SAST and CI status checks.

The initial review and integration checks added actual writable-volume and
unprivileged-user checks, the authenticated LiveKit WebSocket handshake through
Caddy, explicit compatibility evidence for unchanged schema epochs and filtered
credential-bearing query logging. The later password-format change advances
the release epoch to 3; media quarantine integration must advance it to 4.

Native execution, rather than a mocked image assertion, caught the unsupported
setup-java version and then vulnerable application and infrastructure packages.
The supported Java repair is merged. Runtime image updates, the patched Caddy
build and removal of PostgreSQL's unused privilege helper address the remaining
known findings without scanner suppressions. Exact-head native scans and
staging are still merge requirements, including any new actionable review.
