# Issue #126 - change log

**Branch:** `feature/116-v2-app-shell`
**Mockup:** `journey-7-friends-dm.png`

## Summary

- Added the two-pane Friends list and direct-message conversation layout with responsive stacking.
- Connected real friend presence, thread history, realtime messages, sending, and DM voice calls through `useFriendsHub`.
- Added interactive online/all groups, pending requests, add-friend state, message composer, and call controls.
- Kept demo contacts only as a visual fallback for accounts with no established friendships.

## Verification

Final verification uses frontend lint, test, build, and multi-viewport browser checks.
