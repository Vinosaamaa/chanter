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

The Go image is digest-pinned in `infra/production/runtime-lock.json`. Builds
use `GOTOOLCHAIN=local`, `CGO_ENABLED=0` and `-mod=readonly`; dependency versions
cannot drift during a release build. `go.sum` verifies downloaded module bytes.
The resulting binary has no privileged-port capability and runs as uid 10001.

For a security refresh, update explicit dependency versions with `go get`, run
`go mod tidy`, review both lock files and rebuild. Both native release jobs must
pass the complete image scan and actual TLS/static/proxy checks before merge.
Return to an upstream binary only after its own scan passes those same gates.
