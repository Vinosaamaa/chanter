# Issue 316 implementation record

Status: independent native developer package in progress, not a signed or released companion. Accepted rebase baseline is `798eb16cd1f505b6378ff6241cd3c16a430e74ff`; backend integration still waits for #244. See [operator instructions](../../companion/README.md), [design](issue-316-design.md), and [system review](issue-316-system-review.md).

## Implemented slice

The separate `companion` module has a private stdio client for an unmodified provider-owned Codex binary. The client has explicit methods for sanitized account/model/limit discovery, provider-owned device login/cancellation, and a bounded private study turn. A separate signed-request gate serves the fixed loopback study route. It has no generic browser-to-Codex RPC proxy, API-key login, external-token refresh, or purchase/reset action. The launch plan removes inherited tokens, proxies, agent context, plugins, tools, and instruction discovery. Exact supported-version matching starts at CLI 0.153.4.

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

## Protected pairing and signed requests

The internal loopback service now requires the exact Host/Origin, a non-simple pairing header, a deployment-signed request, and explicit native approval. The signed schema binds the installation, current user/session, question, provider/model, mode, input/evidence hashes, bounds, and short expiry. Only quoted evidence is accepted. Native approval cannot hold the service forever; expiry or disconnection releases it. Pairing has no HTTP route. The protected SQLite state retains installation identity and single-use claims across reopen, uses an atomic write before transport, and never releases an uncertain attempt. Windows access-control tests use actual filesystem permissions and reject a deliberately broadened synthetic store.

New tests cover signed scope/expiry/signature failures, changed prompts, malicious websites and spoofed origins without pairing, re-pair/logout, logout during approval, native approval timeout, durable replay rejection, independent state connections, safe provider errors, and actual loopback streaming. One native synthetic flow crosses signed pairing, real HTTP, durable consumption, and unmodified Codex 0.153.4. The inherited fixed skills namespace and operational SQLite retention caveats remain. A shutdown regression proved that an ignored graceful termination needed a forced termination after one second.

The resumed private study method checks the effective ephemeral context, empty instruction/workspace lists, named permissions, and requested model before sending evidence. It bounds UTF-8 input/output and elapsed time, permits one attempt per connection, terminates on cancellation or protocol failure, rejects mismatched turn identifiers and unexpected tool requests, and requires completed answer text to agree with streamed text. Usage preserves zero and unknown and is labeled a native client report. Standard provider speed is explicit. The provider child closes after the attempt.

The missing method and changed completed-answer tests failed before their implementations. The installed unmodified CLI remains 0.153.4. No provider login or external model call occurred. Backend-issued authorization and a real native approval UI still gate product access to this private method. Transport availability alone authorizes no evidence export or subscription inference.

The resumed local suite passes 43 tests with no failures or skips, including eight native Codex tests. `git diff --check` also passes. A dedicated Windows/Linux workflow runs the Node 24 boundary suite without downloading or authenticating a provider binary; its native tests explicitly skip unless a compatible binary is supplied. That workflow has not run on a hosted PR yet, and no cross-platform native entitlement or isolation claim follows from it.

## Native operator package and retention

The unsigned `0.1.0-dev` source installer verifies the selected unmodified Codex version, installs a fixed program-file list and public deployment configuration into a private directory, and produces a visible-terminal launcher. It does not alter PATH, register background startup, import authentication, or run a login. Installation inside a Git workspace writes a private-state ignore file. Setup status distinguishes installation/version checks from live account/listener checks. Existing installations are preserved. Windows is the verified native platform; other native platforms fail closed.

The native terminal displays signed pairing/request details with control characters escaped. A random typed challenge, not a browser response or redirected pipe, grants approval. Expiry clears the challenge. Explicit operator commands own provider device login, cancellation, logout, pairing revocation, status, restart, and stop. A failed stop/restart race test led to serialized lifecycle operations. No real login was invoked during development.

Each provider operation now uses a fresh empty workspace, home, temporary directory, SQLite directory, and log directory. The separate provider-owned authentication home persists. Process exit is verified before deleting the exact owned runtime directory and temporary executable wrappers. Recovery removes an owned orphan only when its recorded controller and provider processes are both absent; linked, live, unknown, or unremovable state blocks execution. Authentication files are never opened, copied, or deleted by Chanter. Source-only/unknown completion claims remain durable.

The local suite passes 53 tests, including nine native cases with unmodified Codex 0.153.4. A real installed package was run in an interactive terminal: status reported signed-out, restart succeeded, stop exited cleanly, and the owned run directory was empty afterward. Native tests also prove that operational SQLite files are absent from the persistent provider home and that a synthetic provider-owned marker survives cleanup. This is local Windows/protocol evidence, not eligible-account inference or signed-release proof.

## Remaining owning-issue work

Signed installers, backend signing/reservations/export approval/result acceptance, signing-key provisioning, hosted pairing UI and live session-revocation wiring, final durable-claim retention policy, and consenting eligible-account end-to-end verification remain required. #321 owns the existing hosted-provider selector/error UI; it does not deliver #316. Claude remains a separate native adapter within this owning issue's acceptance criteria. Full retrieval and grounded study explanation still depend on #247/evaluation. No public service, provider login, copied credential, or paid inference was used in this work.

## Integrated developer slice (supersedes backend/UI gaps above)

The agent now signs pairing/status/study capabilities using an operator-provisioned Ed25519 key. Access tokens carry durable session identity; authenticated private introspection checks live ownership/revocation before native release and result acceptance. Existing session-less web compatibility is preserved. Shared retrieval and the existing one-attempt ledger enforce authorization/reservation; V10 adds immutable native scope and a one-way result claim. The backend validates source quotations and clears temporary evidence, preserving UNKNOWN provider usage for all client reports.

The Windows browser offers an explicit terminal-pairing flow, live signed connection check, deployment/provider model intersection, per-question export consent, and cancellation. Local traffic omits browser credentials and no raw provider output becomes a saved answer. Unsupported devices and unavailable deployments keep web/source choices. Native status is user-triggered, with one fresh check before generation. The native terminal grants five-minute pairings and retains separate per-request approval.

The source workflow now creates a tested archive with exact commit metadata and checksums; trusted dispatch/main runs add free GitHub provenance. This remains OS-unsigned. Provisioning and consenting eligible-account end-to-end proof, Claude support, and final durable-claim retention remain owning-issue gates. No provider login, copied credentials, or paid call occurred.

Local verification passes 55 native tests including nine installed-CLI synthetic cases, auth rotation/logout/replay tests, HTTP native issuance/result/resource-revocation tests, and concurrent claim/retention tests. Frontend build, lint, and bundle budgets pass; the full hosted CI passes on the first integrated checkpoint. Desktop browser review of the actual connection component found and corrected invisible input borders and consent alignment; explicit pairing, connected model/consent, and expired state were visually inspected. This synthetic UI review does not establish a real browser's permission to reach the native listener or an eligible provider account.
