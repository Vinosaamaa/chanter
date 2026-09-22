# Issue #337 system review

## Scope and reachability

Eight open dependency alerts affect the checked-in Caddy Go graph and frontend
test tools. This review does not establish exploitation. Runtime configuration
and dependency presence are separate evidence.

- The [OpenTelemetry gRPC log exporter advisory](https://github.com/open-telemetry/opentelemetry-go/security/advisories/GHSA-w34q-cm8f-9c5x)
  concerns ignored environment TLS certificate settings. All three linked log
  exporters now use 0.21.0 because its changed log value API makes the older HTTP
  and console exporters incompatible. Chanter's Caddyfile does not configure an
  OTLP log exporter.
- The [trace diagnostic advisory](https://github.com/open-telemetry/opentelemetry-go/security/advisories/GHSA-8wmf-6v46-5gfg)
  requires a configured trace exporter and verbose internal diagnostics. The
  locked trace exporters and SDK now resolve to 1.45.0. The shipped Caddyfile
  does not enable tracing or those diagnostics. The upstream endpoint-path API
  change therefore does not alter a configured Chanter exporter.
- The [CEL native field advisory](https://github.com/cel-expr/cel-go/security/advisories/GHSA-gcjh-h69q-9w9g)
  requires native Go struct registration with JSON tag parsing and an expression
  accessing a skipped field. No such registration is present in Caddy 2.11.4's
  matcher, and Chanter does not accept user-authored Caddy expressions. A real
  dependency regression nevertheless reproduced the leak with the advisory's
  nominal patched version 0.29.0. We select 0.30.0, which includes official fix
  `83eed56b0fe697952d3bbae964e5539c16cf3a09`.
- The [Vitest mock redirect advisory](https://github.com/vitest-dev/vitest/security/advisories/GHSA-82fw-gwwq-j7x9)
  affects the development mock server. Vitest and its resolved mocker are pinned
  to 4.1.11. This repository uses jsdom tests, does not register the standalone
  mocker plugin, and does not ship these development packages in its static
  application. Production dependency versions and bundle allowances are unchanged.

## Compatible and reproducible build

Caddy remains 2.11.4 with standard modules and the pinned Go 1.27.1 image. The
patched CEL interpreter requires the exact two-line slice-type change from
official Caddy commit `b2693fb63a30e6d7be0972c3645e9a2c0a500e93`. No newer Caddy
release was available when this update was prepared. The build patches Go's
local vendored copy after checking SHA-256 of both original and result.
Unexpected source fails closed. The shared downloaded module stays untouched,
and `go mod verify` still validates the original module graph. The backport is
explicit source provenance beyond the version shown by `caddy version`.

The image build runs the field-access security regression and upstream Caddy
expression matcher tests with the same backport used to compile the binary.
Actual Caddy adaptation and native AMD64/ARM64 staging remain required. Existing
image scan severities and exceptions are unchanged; the explicit graph review
also covers these moderate/low advisories outside the HIGH/CRITICAL scan gate.
No API, migration, privilege, network, runtime epoch or initial-route budget
change is needed.

## Validation boundary

The initial npm audit reproduced two moderate test-tool findings. The patched
clean install reports none. Local frontend tests hit three typing failures after
timeouts under host memory pressure; all three passed in an isolated one-worker
rerun without changed assertions or timeouts. Exact-head hosted full frontend,
release and CodeAnt results remain acceptance gates. Issue #337 stays open until
root records merged-main and required release verification.
