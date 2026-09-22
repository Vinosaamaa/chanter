# Patched Caddy build

The runtime uses Caddy 2.11.4 with its standard modules. The official image's
June release binary includes dependencies with HIGH advisories; refreshing its
Alpine layer does not rebuild that binary. Build the same Caddy version with
the pinned Go toolchain and the checked-in module graph instead. No scanner
exception is used.

`main.go` and `LICENSE` are from the upstream `caddy_2.11.4_buildable-artifact.tar.gz`
release asset. Its SHA-512 is
`3b7842961de67b5496822f546875353face5b69757bc88a58664946c062a5555a6208368eeec6b3d992d33a809e7376e9c8b39d120e97877cbd3ab3885e7aa8c`.
The original module files are updated only to resolve the reported dependency
advisories and their required compatible transitive versions.

Issue #337 selects cel-go 0.30.0, OpenTelemetry trace/SDK 1.45.0 and log
exporters 0.21.0. The CEL advisory names 0.29.0 as patched, but its dynamic
JSON-excluded-field lookup still fails our regression. Version 0.30.0 contains
the actual [upstream fix](https://github.com/cel-expr/cel-go/commit/83eed56b0fe697952d3bbae964e5539c16cf3a09).

Caddy 2.11.4 needs the two `InterpretableV2` slice types from official commit
[`b2693fb`](https://github.com/caddyserver/caddy/commit/b2693fb63a30e6d7be0972c3645e9a2c0a500e93)
to compile against the patched CEL API. `compat/main.go` verifies the exact
2.11.4 source and resulting backport SHA-256 hashes, then patches only the build-local
vendored copy. It never modifies the module cache. This is Caddy 2.11.4 with that
explicit backport, not an unmodified upstream binary or a prerelease upgrade.
Remove the backport when adopting a released Caddy that contains the fix.

The Go image is digest-pinned in `infra/production/runtime-lock.json`. Builds
use `GOTOOLCHAIN=local`, `CGO_ENABLED=0` and the verified vendored graph; versions
cannot drift during a release build. `go.sum` verifies downloaded module bytes.
The resulting binary has no privileged-port capability and runs as uid 10001.

For a security refresh, update explicit dependency versions with `go get`, run
`go mod tidy`, review both lock files and rebuild. Both native release jobs must
pass the complete image scan and actual TLS/static/proxy checks before merge.
Return to an upstream binary only after its own scan passes those same gates.

The reproducible build runs `go mod download`, `go mod verify`, `go mod vendor`, and
`go run -mod=readonly ./compat` before compiling with
`-mod=vendor`. It runs the excluded-field regression,
source-identity rejection test and upstream Caddy expression matcher tests.
Packaged staging then adapts the real production Caddyfile and verifies the
private media guard ordering before serving TLS/static/API traffic.
