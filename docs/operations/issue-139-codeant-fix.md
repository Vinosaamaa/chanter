# Issue #139 - CodeAnt remediation log

## Round 0 — initial PR (#155, commit `b2cea5c`)

- **CodeAnt Quality Gates:** pass
- **SAST / SCA / SCR / Test Coverage:** pass
- **CI note:** first backend run failed on unrelated flaky `SocialRealtimeWebSocketSmokeTest` timeout; rerun passed.
- **Inline review:** five Critical suggestions left on commit `b2cea5c` (addressed in Round 1).

## Round 1 — Critical inline findings

| Finding | Severity | Action |
|---------|----------|--------|
| Create Study Server + invites not atomic (partial server on invite failure) | Critical | Pre-resolve all invite emails before persist; wrap create in `@Transactional` so any failure rolls back |
| `GET /study-servers/{id}` leaked `pendingInvitations` without owner check | Critical | Return pending invite emails only when requester is the Study Server owner |
| `inviteEmails` allowed null elements → NPE on `trim()` | Critical | Constrain list elements with `@NotNull @NotBlank @Email` |
| Concurrent `addCohort` race (check-then-insert) | Critical | `SELECT … FOR UPDATE` on course before presence check + insert |
| Archived courses could be re-published | Critical | Block publish when `archived`; also require a cohort before publish |

**Tests added:** unknown-invite rollback, null invite element → 400, non-owner empty pending invites, publish archived → 409, publish without cohort → 409, second cohort → 409.
