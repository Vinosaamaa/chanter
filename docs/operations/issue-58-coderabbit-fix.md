# Issue #58 — CodeRabbit fix log (PR #75)

## Pass 1

| Comment | Action |
|---------|--------|
| Exclude support-question rows from reply metrics/timeline | Fixed — exclude `channel_message_id` from reply counts and timeline `REPLY_ADDED` events |
| Filter approved FAQs to summary window | Fixed — `updatedAt` must fall within `[windowStart, windowEnd)` for digest + timeline |
| Defensive `List.copyOf` on follow-ups / text-items records | Fixed |
| Add `401` smoke test for `/channel-summary` | Fixed |
| Clarify optional `windowDays` in change log | Fixed |
| Distinguish navigation fetch errors from instructor-only gate | Fixed |
| Add time-window selector in UI | Fixed — 7 / 14 / 30 day dropdown |
| Reset mutation when `channelId` or `windowDays` changes | Fixed |

## Pass 2

| Comment | Action |
|---------|--------|
| Fetch approved FAQs once per summary request | Fixed — `approvedFaqsInWindow` shared by digest + timeline |
| Stabilize `useMutation` reset effect deps | Fixed — destructure `reset`/`mutate` instead of whole `mutation` object |

## Deferred

None.
