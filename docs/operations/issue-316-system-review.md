# Issue 316 system review

Status: initial boundary review, not release approval. This review separates native observations from the remaining product security work.

| Boundary | Observed evidence | Remaining requirement |
| --- | --- | --- |
| Authentication ownership | Fixed provider-managed device flow in the stdio client; no external-token/API-key method; real signed-out native handshake | Eligible-account login and complete native user flow; provider-owned store permissions and reconnect UX |
| Native execution | CLI 0.153.4 strict configuration; empty environment roots; no instruction files; fixed skills namespace; planted skill/forged path/unadvertised shell fixtures disclose no canary | More provider-home and platform fixtures; installer compatibility/signatures; fail closed on unsupported versions or changed tool surface |
| Transport | One MiB frame bound, UTF-8 input/output bounds, deadline/abort termination, immediate EOF rejection, fixed safe error codes, no diagnostic logging; real native synthetic turn through private production client | Product authorization integration; cross-platform shutdown evidence; input/output bounds do not establish a provider billing limit |
| Account/model discovery | Sanitized plan/model/window DTOs, finite pagination, hidden/malformed filtering, no probes for unsupported auth | Refresh/reconnect UI and provider-account-specific usage policy; a plan label is not free-call authorization |
| Data retention | Native ephemeral thread and `store=false` verified; operational databases still created | Inspect synthetic-content retention, define deletion, prove no secrets/content in diagnostics; provider retention remains separately governed |
| Website/native boundary | No localhost service or generic RPC proxy exists in this slice | Exact Origin/Host checks, explicit native pairing, protected per-install secret, user/session binding, replay-resistant capability, native request approval |
| Backend trust | Existing #248 reservation/evidence semantics identified for reuse; no backend behavior changed | Current grants/resource availability/export consent at release and acceptance; atomic reservation and consumed capability storage; untrusted usage provenance |
| Intelligent tutor | Native fixture exercises protocol, not a semantic answer | #247 retrieval and semantic evaluations; keep Source only, quoted evidence, and grounded explanation distinct |

The process uses a separate provider home rather than importing the existing user's Codex credential/configuration directory. This avoids inheriting personal integrations but requires a provider-owned sign-in for the companion. Do not silently weaken that boundary to reuse existing tokens. A local attacker who can replace the executable or edit the companion binary is outside the browser pairing boundary; release signing and OS-owned state protection still matter.

The fixed `skills` namespace is an observed provider behavior, not an intentionally exposed general file API. Both authorities returned no skills, and a forged absolute package/resource was unavailable. These finite fixtures are stronger than a prompt instruction but are not a universal proof for every platform or future CLI. Pin compatibility, retain adversarial native tests, and reject additional tools or nonempty skill catalogs before releasing evidence.

No known deployment is enabled by the current module. The private production turn checks effective thread isolation before input, rejects unexpected tools and mismatched stream/completion content, reports client-claimed usage, and closes its provider process after one attempt. It has no browser or backend entrypoint. Product generation, pairing, and capability acceptance must remain inaccessible until their owning security gates and backend dependencies are implemented and verified. No issue closure, paid call, provider entitlement, signed installer, or production readiness is claimed.
