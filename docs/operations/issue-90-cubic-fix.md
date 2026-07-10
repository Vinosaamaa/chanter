# Issue #90 — cubic fix log

## Pass 1 (PR #110)

| Finding | Severity | Action |
|---------|----------|--------|
| Friend-request query cache not scoped to user id | P1 | **Fixed** — `friendRequestsQueryKey(userId)` |
| `actionError` persists across inbox tabs | P2 | **Fixed** — clear on tab switch |
| `agent-workflow.md` vs change-log browser reveal mismatch | P2 | **Fixed** — both document `open_resource` + `position: "active"` |
| `HANDOFF.md` startup prompt still linked #93 / PR #108 | P2 | **Fixed** — #90 / PR #110 |
| Stale `supervisor.pid` could kill wrong process | P2 | **Fixed** — verify `supervise.sh` in cmdline before signal |
| Detached start breaks on paths with spaces | P2 | **Fixed** — `bash -c` positional args for java/npm |
| Block with no confirmation | P2 | **Fixed** — `window.confirm` before block |
| Concurrent `make product-supervise` race | P2 | **Fixed** — write supervisor pid before infra/build |
| Supervisor comment implied child lifecycle | P2/P3 | **Fixed** — accurate header comment |
| `pendingActionId` single-slot busy state | P3 | **Fixed** — `Set` of pending ids |
| Online tab vs selected offline friend pane | P3 | **Fixed** — derive selection from visible list |
| Top nav `aria-current` on `/app/friends/*` | P3 | **Fixed** — exact path match for `aria-current` |
| Friends Hub nav sidebar landmark | P3 | **Fixed** — `aria-label` on sidebar |
| Duplicate FriendRequest row mapper in JDBC | P3 | **Deferred** — low risk; extract mapper in follow-up |
| Supervisor does not own service PIDs on exit | P2 | **Deferred** — documented; `make product-down` is the supported stop path; refactor tracked with product stack reliability work |

### Verification (pass 1)

```bash
cd frontend && npm run test -- --run friends-api FriendRequestsPage
cd frontend && npm run lint && npm run build
# backend: CI FriendRequestAndDirectMessageSmokeTest
```

## Deferred (pre-existing / stretch)

- Display-name resolution for inbox rows (needs user-profile API) — PL-09 / #90 stretch.
- Realtime invalidation of friend-request badge (react-query refetch on focus is sufficient for beta).
