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

## Review of the runtime repair

Full reviews completed against `b9b3892` and `4588514`. The latter also passed
both native release jobs, including all 14 final-image scans and actual staging.
This remediation changes the release candidate, so its own full checks and
review must complete before merge.

- Release asset collisions: serialize each commit/architecture publisher. A
  retry verifies existing GitHub asset digests and uploads only missing files.
  Different or unverifiable existing bytes are preserved and fail the run;
  published releases cannot acquire missing assets. Regression covers partial
  retry, differing bytes and the published-release boundary. No clobber flag.
- SSH public-key prefix alone: reproduced with an empty key accepted by an
  actual provider-free OpenTofu plan. Require the complete Ed25519 wire format
  and single-line optional comment. Native plans now reject empty/malformed
  values and accept a correctly encoded synthetic key. Possession of the
  matching private key remains an operator provisioning check.
- Password in psql arguments: reproduced at the actual shell/process boundary.
  psql now reads its already-private container environment with `\getenv`;
  only the environment variable's name appears in arguments. The regression
  confirms no password argument; native staging verifies actual database setup.
- LiveKit startup race: add a bounded readiness wait to the smoke path, matching
  the host deployment's existing wait.
- Redirect to internal port 8443: not supported by Caddy's documented contract.
  Global `http_port`/`https_port` change internal listeners, not client ports.
  Retain the unprivileged configuration and add an actual public redirect
  assertion to native staging. See the [Caddy global-port documentation](https://caddyserver.com/docs/caddyfile/options#https-port).

Generated host-test fixtures are removed through their test cleanup callbacks,
after checking their resolved paths remain beneath the dedicated scratch root.

The image OCID string check does not establish origin or architecture. The
existing provisioning checkpoint requires the operator's verified regional
official ARM64 image and reviewed plan; no account or image has been selected.
This remains an actual-provider gate, not an inferred property of the string.

Keep native architecture builds, a real second Flyway migration invocation,
bounded sequential startup and one scanner process per image. They establish
distinct compatibility or failure evidence within the small-host contract.
The scanner already shares its database cache. Bulk startup, generic compose
factories, manifest batching and render caching are optional optimizations
without a demonstrated failure here. The PostgreSQL helper's lower-layer bytes
remain in the archive but cannot execute in the final runtime filesystem;
rebuilding PostgreSQL solely to save those bytes would add maintenance scope.
The migration helper deliberately targets this package's fixed private database
endpoint. Its readiness helper only requests owned small health responses.
Engineering verification refers to the cited implementation checks; unknowns
and the public runbook retain the separate production gates.

## Follow-up review

- Repeated smoke initialization: fixed with a unique scratch directory and
  Compose project per invocation. A retry cannot reuse prior credentials or
  delete another invocation's volumes; failed diagnostics remain in ignored
  runner scratch storage. Unsupported architecture arguments fail before setup.
- Lock left after an abrupt process crash: retained deliberately. Automatic
  stale-lock deletion without proving process ownership could overlap a live
  deployment. The runbook already requires inspection and removal of the empty
  lock only after confirming no deployment process remains. This is bounded
  operator recovery, not a claim that process-kill recovery is automatic.
- Missing PostgreSQL-only migration location: disproven by the actual native
  staging run at `4588514`, which successfully migrated all seven service
  databases twice on both architectures. Flyway's missing-location behavior
  does not match the reported failure. The same native proof remains required
  on the final candidate.
