# Issue #58 — Channel Summary UI For Course Channels

## Summary

Instructors can generate an on-demand digest for a `#questions` course channel: metric cards, AI-style digest sections (top topics, follow-ups, decisions, resources), and an activity timeline. Learners receive 403 from the API and a friendly message in the UI.

## API

`POST /api/v1/course-channels/{channelId}/channel-summary`

```json
{ "windowDays": 7 }
```

- **Auth:** instructor only (`canViewUnansweredSupportQuestions` via course channel access).
- **MVP aggregation:** support questions + channel messages in the window; topic clustering reuses `FaqCandidateGrouper`; approved FAQs feed decisions/resources.
- **Views metric:** estimated from question/reply counts (no read-tracking yet).

## Frontend

| Route | Purpose |
|-------|---------|
| `/app/servers/:serverId/course-channels/:channelId/summary` | Channel Summary page |

`frontend/src/features/channel-summary/`

- `channel-summary-api.ts` — generate summary + display helpers
- `components/ChannelSummaryPage.tsx` — mockup-aligned layout
- Link from `#questions` channel header when `canViewFullCatalog`

## TDD

- `ChannelSummarySmokeTest` (message-service)
- `channel-summary-api.test.ts` (Vitest)

## Browser test (manual)

1. Sign in as instructor on a Study Server with `#questions` activity.
2. Open `#questions` → **Channel summary**.
3. Click **Generate summary** → metrics, digest, and timeline populate.
4. **Export PDF** opens the browser print dialog.
5. Sign in as learner → summary route shows unauthorized message; API returns 403.

## Deferred

- Persisted summary snapshots / scheduled weekly generation
- Real view counts and resolution timestamps
- PDF export beyond `window.print()`
- Media-service resource links in digest
