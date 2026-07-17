# Codebase Hardening — Issue Breakdown

**Epic:** [#180](https://github.com/Vinosaamaa/chanter/issues/180)  
**Project board:** [Codebase Hardening #7](https://github.com/users/Vinosaamaa/projects/7)  
**Findings report:** [`docs/operations/codebase-review-2026-07-16.md`](../operations/codebase-review-2026-07-16.md)  
**Findings PR:** [#179](https://github.com/Vinosaamaa/chanter/pull/179) (docs-only; **open**)

Public-beta feature slices through **#104** are merged on `main`. This phase remediates security, correctness, and ops findings from the 2026-07-16 full-repo review **before** broader post-launch product work ([#107](https://github.com/Vinosaamaa/chanter/issues/107)).

**Workflow:** one finding (or tightly related pair) → one branch → one PR per [`agent-workflow.md`](../operations/agent-workflow.md). Add regression tests where practical (see report §5.5).

---

## Findings → issues map

| Finding | Severity | Issue | Status |
|---------|----------|-------|--------|
| SEC-04 | High | [#181](https://github.com/Vinosaamaa/chanter/issues/181) Reject default JWT/internal secrets | Todo |
| SEC-02 | High | [#182](https://github.com/Vinosaamaa/chanter/issues/182) agent-service gateway identity | Todo |
| SEC-03 | High | [#183](https://github.com/Vinosaamaa/chanter/issues/183) DM-call LiveKit token auth | Todo |
| SEC-06 | High | [#184](https://github.com/Vinosaamaa/chanter/issues/184) Refresh token out of localStorage | Todo |
| SEC-10 | High | [#185](https://github.com/Vinosaamaa/chanter/issues/185) Gate `/dev/demo` from prod | Todo |
| SEC-05 | High | [#186](https://github.com/Vinosaamaa/chanter/issues/186) OAuth `email_verified` | Todo |
| SEC-07 / BUG-02 | Medium | [#187](https://github.com/Vinosaamaa/chanter/issues/187) Gateway public auth paths | Todo |
| SEC-01 | High | [#188](https://github.com/Vinosaamaa/chanter/issues/188) Service-level identity enforcement | Todo |
| SEC-08 | Medium | [#189](https://github.com/Vinosaamaa/chanter/issues/189) Auth rate limiter client IP | Todo |
| SEC-09 | Medium | [#190](https://github.com/Vinosaamaa/chanter/issues/190) OAuth state + PKCE | Todo |
| SEC-11 | Medium | [#191](https://github.com/Vinosaamaa/chanter/issues/191) WS auth without URL token | Todo |
| SEC-12 | Medium | [#192](https://github.com/Vinosaamaa/chanter/issues/192) Infra binds + default creds | Todo |
| SEC-13 | Medium | [#193](https://github.com/Vinosaamaa/chanter/issues/193) CI least-privilege permissions | Todo |
| SEC-14 | Medium | [#194](https://github.com/Vinosaamaa/chanter/issues/194) Pin Docker image tags | Todo |
| BUG-01 | Medium | [#195](https://github.com/Vinosaamaa/chanter/issues/195) WS reconnect token refresh | Todo |
| SEC-15 | Low | [#196](https://github.com/Vinosaamaa/chanter/issues/196) Register enumeration | Todo |
| SEC-16 | Low | [#197](https://github.com/Vinosaamaa/chanter/issues/197) Login timing side-channel | Todo |
| SEC-17 | Low | [#198](https://github.com/Vinosaamaa/chanter/issues/198) Media content-type allowlist | Todo |
| SEC-18 | Low | [#199](https://github.com/Vinosaamaa/chanter/issues/199) Restrict actuator info | Todo |
| SEC-19 | Low | [#200](https://github.com/Vinosaamaa/chanter/issues/200) Env-driven CORS | Done |
| SEC-20 | Low | [#201](https://github.com/Vinosaamaa/chanter/issues/201) Rate limiter memory bounds | Done |
| SEC-21 | Low | [#202](https://github.com/Vinosaamaa/chanter/issues/202) Blank internal token rejection | Done |
| SEC-22 | Low | [#203](https://github.com/Vinosaamaa/chanter/issues/203) OAuth URL scheme validation | Done |
| BUG-03 | Low | [#204](https://github.com/Vinosaamaa/chanter/issues/204) LIKE wildcard escape | Done |
| BUG-04 | Low/Info | [#205](https://github.com/Vinosaamaa/chanter/issues/205) Announcements write role | In progress |

---

## Recommended implementation order

Matches report §6 and epic #180.

| Order | Issue | Title |
|------:|-------|-------|
| 0 | [#179](https://github.com/Vinosaamaa/chanter/pull/179) | Merge findings report (docs-only) |
| 1 | [**#181**](https://github.com/Vinosaamaa/chanter/issues/181) | **← START HERE** Reject default JWT and internal-service secrets (SEC-04) |
| 2 | [#182](https://github.com/Vinosaamaa/chanter/issues/182) | agent-service gateway identity headers (SEC-02) |
| 3 | [#183](https://github.com/Vinosaamaa/chanter/issues/183) | Authenticate internal DM-call LiveKit token minting (SEC-03) |
| 4 | [#184](https://github.com/Vinosaamaa/chanter/issues/184) | Stop persisting refresh token in localStorage (SEC-06) |
| 5 | [#185](https://github.com/Vinosaamaa/chanter/issues/185) | Gate `/dev/demo` out of production builds (SEC-10) |
| 6 | [#186](https://github.com/Vinosaamaa/chanter/issues/186) | Require Google OAuth `email_verified` (SEC-05) |
| 7 | [#187](https://github.com/Vinosaamaa/chanter/issues/187) | Allow public auth paths through gateway (SEC-07 / BUG-02) |
| 8 | [#188](https://github.com/Vinosaamaa/chanter/issues/188) | Enforce service-level identity beyond `X-User-Id` (SEC-01) |
| 9+ | [#189](https://github.com/Vinosaamaa/chanter/issues/189)–[#205](https://github.com/Vinosaamaa/chanter/issues/205) | Remaining Medium / Low / bug backlog (board order) |

After all High and blocking Medium items are merged, resume post-launch product work under [#107](https://github.com/Vinosaamaa/chanter/issues/107).
