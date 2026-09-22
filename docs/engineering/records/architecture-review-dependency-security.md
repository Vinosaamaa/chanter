---
schemaVersion: 1
id: architecture-review-dependency-security
revision: 1
type: architecture-review
status: proposed
title: "Patch Caddy and Vitest security dependencies"
repository: chanter
capabilityIds: ["chanter-social-collaboration-mvp"]
createdAt: 2026-09-22
reconstructed: false
confidence: high
unknowns: ["Exact-head hosted release and merged-main acceptance remain required."]
modules: ["production-frontend", "frontend-test-tools"]
interfaces: ["caddy-http-runtime", "frontend-test-runner"]
seams: ["locked-dependencies-to-native-runtime"]
adapters: ["caddy-cel-compatibility-backport"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Reproducible patched runtime"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Dependency security scope", "url":"https://github.com/Vinosaamaa/chanter/issues/337", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:TestCELDoesNotExposeJSONExcludedFields", "test:TestBackportRejectsUnexpectedSource", "test:TestMatchExpression", "command:npm-audit"]}
visibility: public-safe
publicationEligibility: eligible
issue: 337
pr: null
release: null
run: null
---
# Patch Caddy and Vitest security dependencies

Eight dependency findings require patched Go and frontend test graphs. Caddy
remains 2.11.4 with OpenTelemetry trace/SDK 1.45.0, log exporters 0.21.0 and CEL
0.30.0. Vitest and its mocker resolve to 4.1.11. Production frontend dependencies,
service contracts, runtime epoch and bundle limits remain unchanged.

Compiling the patched CEL graph exposed an API mismatch in the latest released
Caddy. The smallest compatible change is the two-line official upstream
interpreter backport. A build-only vendored copy verifies original and result
hashes, preserves the module cache and avoids an unreleased Caddy upgrade or
custom fork. Its maintenance cost is explicit: remove it when a released Caddy
contains the change, after native release verification.

Advisory version metadata alone was insufficient. The JSON-excluded-field
regression still leaked on nominally patched CEL 0.29.0, so the selected 0.30.0
includes the actual upstream fix. The native image build runs this behavior test,
the source-identity rejection check and upstream expression matcher tests before
compiling the runtime. Actual Caddy adaptation, native architecture staging,
unchanged vulnerability scans and exact-head CI remain integration gates.

The system review documents each advisory's configuration prerequisites. Current
Chanter configuration does not establish those vulnerable runtime paths, but
the graph is patched without claiming that dependency presence proves exposure
or dismissing alerts on that basis.
