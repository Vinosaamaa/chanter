# Issue #31 — cubic fix log

> Record every cubic Dev AI review pass here. One row per finding; note fix or explicit deferral.

## Pass 1

| Comment | Action |
|---------|--------|
| CI: `sentAt` nanosecond vs microsecond mismatch in DM smoke test | Fixed — truncate to `ChronoUnit.MICROS` in `SocialMessagingService.sendDirectMessage` |
| CI: `react-hooks/set-state-in-effect` on friends hub message load | Fixed — derive `isLoadingMessages` from `loadedMessagesFriendId` vs `selectedFriendId` |
| P2: Failed DM history leaves endless loading spinner | Fixed — on fetch error, set `loadedMessagesFriendId` to selected friend with empty messages |
| P2: Optimistic send while thread loading attaches to wrong friend cache | Fixed — claim thread ownership on send; guard realtime merges with `loadedMessagesFriendIdRef` |

Verification: `npm run lint`, `FriendRequestAndDirectMessageSmokeTest`, CI on PR #79.

## Deferred

None.
