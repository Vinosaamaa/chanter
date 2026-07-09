# Issue #86 — cubic fix log

## Pass 1 (16 findings)

| Severity | Comment | Action |
|----------|---------|--------|
| P1 | `HttpChannelSubscriptionAuthorizer` broad `catch (Exception)` swallowed `ResponseStatusException` (403 → 502) | Fixed — rethrow `ResponseStatusException` before generic fallback |
| P1 | `HttpApprovedFaqClient` missing `viewerUserId` query param (message-service requires it) | Fixed — send query param + `X-User-Id` header |
| P1 | Cleanup script deleted all owner servers, not just duplicate demo name | Fixed — delete only `name = 'Workable Product Demo'` duplicates |
| P2 | `agent-workflow.md` startup snippet still pointed to #48 / project #3 | Fixed — Public Launch #87 / project #5 |
| P2 | `HANDOFF.md` stale #63 branch / startup prompt | Fixed — Public Launch active slice; updated New Chat prompt |
| P2 | `create-public-launch-issues.sh` no duplicate guard | Fixed — abort if milestone already has issues |
| P2 | Epic UI body wrong issue range `#68–#75` | Fixed — reference breakdown doc dynamically |
| P2 | ISSUE_79 body literal `#76` / `#80` | Fixed — generic wording (script already run) |
| P2 | `story()` depended on `rg` | Fixed — use `sed` for issue number suffix |
| P2 | Demo password JSON interpolation in seed/cleanup | Fixed — `json.dumps` via python3 |
| P2 | `product_module_jar` lexicographic first jar | Fixed — `ls -t` newest artifact |
| P2 | No `lsof` prerequisite | Fixed — `product_require_lsof` in `product-up` |
| P2 | `product_stop_module` could kill foreign port listener | Fixed — prefer recorded pid; warn on foreign listener |
| P2 | `up.sh` java cwd broke media relative storage paths | Fixed — launch from `$ROOT/backend` |
| P3 | README Next Milestone linked closing issue #86 | Fixed — link project #5 + #87 next |
| P3 | Redundant `disown -h` after `nohup` | Fixed — removed; use subshell + backend cwd for Java |

## Pass 2

| Severity | Comment | Action |
|----------|---------|--------|
| P2 | Milestone duplicate guard only scanned first 100 repo issues | Fixed — `gh issue list --milestone` query |
| P2 | Foreign port listener skip still removed pid file on next down | Fixed — `return 0` before pid file cleanup |
| P3 | Pass 1 heading said 18 findings, table has 16 | Fixed — heading count |
| P3 | Phase 4 table still marked #86 as START HERE | Fixed — #87 start; #86 noted as PR #105 |

## Pass 3

Pending re-review after push.
