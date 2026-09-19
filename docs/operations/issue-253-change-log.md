# Issue 253 implementation

The first checkpoint adds exact trusted-peer identity, strips spoofed authority,
and tests actual outgoing headers through a running gateway. The Spring outbound
filter initially removed the validated headers; a final canonical header filter
fixed the failing HTTP regression.

The next checkpoint adds hashed shared IP/user/tenant-user budgets and explicit
bounded recovery during Redis failures. Focused tests and the gateway Maven
verify pass. The four real-Redis tests compile and skip locally because no Redis
is provisioned here. CI supplies an isolated Redis and runs those tests outside
the hermetic environment wrapper, which deliberately removes CHANTER variables.

The real Redis suite subsequently passed all four cases in hosted CI, including
two independent gateways and the outage/recovery path. The production generator
now requires Redis admission and dedicated key material, uses an isolated proxy
network and bounds request memory, duration and active work. Browser policy and
optional challenge/email-alternative controls are implemented. Local gateway/auth
Maven verify, frontend build and deployment tests pass.

Hosted regressions and review fixes are recorded in `issue-253-codeant-fix.md`.
Final exact-head hosted acceptance, browser screenshot inspection and provider
proof remain required. No public launch or enabled production protection is claimed.
