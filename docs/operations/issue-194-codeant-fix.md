# Issue #194 CodeAnt Fix Log

## Round 1 — commit `32cce16`

| Gate | Result |
|------|--------|
| Quality Gates | pass |
| SAST | pass (no secrets) |
| SCA | pass |
| SCR | pass (0% duplicate) |
| Test Coverage | pass |
| IAC | pass with **Rating B: 1 medium** (no inline PR comments) |

### Deferrals

- **IAC Rating B (1 medium):** Quality gate still passed; CodeAnt left no actionable inline comment describing the finding. Likely a general Compose/Dockerfile hygiene advisory rather than a pin regression. Deferred pending a clearer CodeAnt detail link; pins themselves match Redpanda/MinIO/LiveKit immutable-tag style.

No code changes from CodeAnt on this issue.
