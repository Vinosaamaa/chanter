# Issue #93 — cubic fix log

## Pass 1 (7 findings)

| Severity | Comment | Action |
|----------|---------|--------|
| P1 | `listAccessibleStudyServers` N+1 `memberCount` queries | Fixed — batch `memberCountsForStudyServers` (2 queries total) |
| P2 | Delete dialog missing Escape / backdrop dismiss | Fixed — `DeleteStudyServerDialog` with backdrop button + Escape |
| P2 | Delete dialog no focus trap | Fixed — focus first control, Tab wrap, restore on close |
| P2 | `HANDOFF.md` startup prompt still pointed at #87 | Fixed — #93 branch, issue URL, PR #108 |
| P2 | Cross-service orphan data on Study Server delete | Deferred — community DB cascades; message/agent cleanup belongs in horizontal-scale hardening (#82 epic) |
| P3 | Duplicate `initials()` in picker + rail | Fixed — shared `study-server-initials.ts` |
| P3 | Change log generic “server” wording | Fixed — “Study Server” in user-facing prose |

## Pass 2

Pending re-review after push.
