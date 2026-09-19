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

This issue remains in implementation. Production wiring, payload/concurrency
bounds, browser policy, optional bot proof, hosted acceptance and provider proof
are still required. No public launch or enabled production protection is claimed.
