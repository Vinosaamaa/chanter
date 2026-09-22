# Issue #337 change log

The owning scope is [#337](https://github.com/Vinosaamaa/chanter/issues/337).
This change patches Caddy's affected dependency graph and pins Vitest's test
tools without changing production frontend dependencies or core budgets.

## Implementation

- Vitest and all its resolved tool packages use 4.1.11.
- OpenTelemetry trace/SDK uses 1.45.0 and log exporters use the compatible 0.21.0
  API. Required transitive versions are locked in `go.mod` and `go.sum`.
- CEL uses 0.30.0. A dynamic JSON-excluded-field test failed on 0.29.0, despite
  that version being listed as patched in advisory metadata.
- Caddy 2.11.4 receives only the official two-line interpreter compatibility
  backport through a source-hash-checked build backport. Image builds test it and
  the actual security behavior before compiling. Production image pins,
  non-root execution and media authorization configuration remain unchanged.

## Verification

Use `npm ci`, `npm test`, `npm run lint`, and `npm run build` in `frontend`.
The build retains all existing bundle gates. `npm audit --package-lock-only`
changed from two moderate findings to none. The initial local full suite passed
322/325; the three timeout-affected tests passed with one worker and unchanged
assertions. Lint and production build pass with unchanged budgets. Hosted full CI
passes at `70a795e7`, including the product journey.

In `infra/production/frontend/caddy`, run `go mod verify`, `go mod vendor`,
`go run -mod=readonly ./compat`, then `go test` and `go build` with
`-mod=vendor`. The Dockerfile also runs
upstream `TestMatchExpression` tests. These tests and the native patched binary
build pass. After the accepted monitoring rebase, all 64 deployment tests pass,
including actual Caddy adaptation.
Run deployment tests with
`CHANTER_CADDY_BINARY` pointing at the rebuilt binary to exercise actual Caddy
adaptation. Both native release jobs pass packaged staging and scans at
`70a795e7`; final exact-head gates cover the accepted monitoring union.

The [system review](issue-337-system-review.md) records official advisory
sources, conditional reachability and backport provenance. One issue-linked PR,
full CodeAnt review and exact-head CI/release checks are required before root
integration. This issue is not closed by the PR.
