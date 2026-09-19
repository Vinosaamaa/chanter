# Issue 250 CodeAnt review dispositions

PR #323 received a completed full review on its initial candidate. The actionable finding was that `useV2SidebarData().isError` combined navigation failures from every accessible server, allowing a failure in another server to block otherwise valid owner usage. A regression reproduced the failure. Usage now waits for its owned-server list and selected server details only; missing ownership still redirects and actual access-loading failures remain visible. Six focused Usage tests, lint and production build pass after remediation.

The remaining suggestions do not identify further incorrect behavior:

- Keep the dedicated Spring test context with limit 17: it proves a real HTTP operator override without changing unrelated test defaults.
- Keep the rejected-mode configuration property: validation intentionally prevents startup under an unsupported paid mode, and configuration tests establish that contract.
- The new Usage browser tests use `@usage`. Retagging the older cross-browser suite and extracting shared legacy fixtures are separate maintenance work.
- Fixed synthetic counts and limits keep visual expectations explicit. Naming constants, shortening the owner-tampering test name and consolidating warning markup are optional style changes.
- The real-service journey retains its Teaching navigation assertion before testing Usage, preserving existing product coverage.

The second completed review identified empty/plain-text HTTP 502 responses bypassing temporary-unavailability guidance. Two new hook cases failed before moving the status check outside JSON parsing; all three body variants now return the same useful guidance. The navigation mock regression deliberately guards a removed dependency: it failed against the prior page and passes after removing that dependency. Reintroducing the failing sidebar gate makes it fail again. The suggested usage-only hook would remove the selected server ownership check and duplicate existing loading/error logic, so the verified shared hook remains. A suggested evidence table is omitted under the current concise-documentation instruction.

The final candidate requires fresh exact-head CI and a completed full review. Review completion and final acceptance evidence are recorded on the PR before integration. Paid-provider and production-release requirements remain open in #250.
