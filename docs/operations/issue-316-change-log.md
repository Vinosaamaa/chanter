# Issue 316 implementation record

Status: initial native boundary in progress, not an installed or released companion. No PR is published yet. Baseline is `db480a3cefb5f30ad109157590064ba0f952abb9`; #319/#244 integration is held pending their accepted baseline. See [design](issue-316-design.md) and [system review](issue-316-system-review.md).

## Implemented slice

The separate `companion` module has a private stdio client for an unmodified provider-owned Codex binary. The client has explicit methods for sanitized account/model/limit discovery and provider-owned device login/cancellation. It has no generic browser-to-Codex RPC proxy, API-key login, external-token refresh, purchase/reset action, or production generation method. The launch plan removes inherited tokens, proxies, agent context, plugins, tools, and instruction discovery. Exact supported-version matching starts at CLI 0.153.4.

Model discovery is limited to five pages of at most 100 entries, rejects a repeated cursor, filters hidden/malformed models, and never probes models or limits for signed-out/API-key/unknown account categories. External-token login and refresh methods are unavailable; the ChatGPT account category alone does not prove authentication provenance. Usage windows preserve measured zero and unknown separately. Native diagnostics are drained without logging; protocol errors expose stable codes. Oversized frames, disconnected output, and timeouts terminate pending requests and the provider child. Managed login allows only the fixed provider device-code flow; its transient code belongs in local user-facing UI, never a log or backend payload.

## Vertical verification

Each behavior began with a failing public-interface test and minimal implementation. The EOF regression first produced `PROVIDER_TIMEOUT` after ten seconds; the fix now fails immediately with `PROVIDER_DISCONNECTED`. Malformed model collections first threw a raw TypeError; they now fail the model validation boundary. Native probes found and corrected the permission-profile CLI quoting and exposed the remaining fixed skills namespace rather than assuming feature flags removed every tool.

Commands:

```sh
node --test companion/tests/codex-account.test.mjs companion/tests/codex-app-server.test.mjs companion/tests/codex-launch.test.mjs
# Set CHANTER_CODEX_TEST_BINARY to the supported user-owned native executable.
node --test companion/tests/*.test.mjs
```

The 2026-09-12 initial checkpoint passed all 21 tests, including six native tests, with no failures or skips. Native tests explicitly skip when no executable is supplied; a skipped test does not establish native compatibility. The Windows fixture uses a fresh provider-owned home and a synthetic HTTP service bound only to loopback. It never logs in, reads/copies an existing authentication file, invokes an external model, consumes subscription usage, or purchases resources. Its native turns exercise the unmodified binary and real Responses event handling, but do not prove individual subscription eligibility or a real tutor answer.

Native assertions cover ephemeral threads, no loaded instruction sources/runtime roots, upstream `store=false`, the exact skills-only tool surface, empty skill catalogs despite a planted host skill, refused forged package reads, and refused unadvertised shell calls without canary disclosure. Unit fixtures cover managed auth selection, device login/cancel, safe errors, timeout/EOF/frame bounds, model pagination, and unknown usage. No frontend or backend feature files changed in this slice.

## Remaining owning-issue work

The native launch entrypoint and secure state storage, signed installers, pairing/origin and single-use capability protocol, backend reservations/export approval/result acceptance, production bounded native streaming and cancellation, local approval UI, operational database retention audit, and consenting eligible-account end-to-end verification remain required. #321 owns the existing hosted-provider selector/error UI; it does not deliver #316. Claude remains a separate native adapter within this owning issue's acceptance criteria. Full retrieval and grounded study explanation still depend on #247/evaluation.
