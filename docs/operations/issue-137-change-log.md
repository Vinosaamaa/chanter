# Issue #137 - change log

**Branch:** `feature/137-course-chat-channel-management-voice`
**Commit:** `feat(course-chat): #137 operationalize Cohort chat and voice`

## Goal

Replace the fixture-driven Course Chat page with Cohort-scoped durable text channels, realtime history and sending, authorized channel management, and real LiveKit voice entry while preserving the approved UI v2 layout.

## What changed

### Cohort-owned Course Channels

- Added `cohort_id` and `archived_at` to `course_channels`; legacy channels are backfilled only when their Course has exactly one Cohort, while ambiguous or orphaned data fails migration instead of being guessed.
- Removed the legacy `UNIQUE(course_id, name)` constraint with a dialect-neutral Java migration. Active names are unique within one Cohort, archived names can be reused, and different Cohorts can use the same name.
- Cohort row locking serializes name checks and position allocation so concurrent channel creation cannot produce duplicate positions.
- New Courses receive Cohort-scoped `announcements`, `questions`, `resources`, and `study-room` channels.
- Study Server owners and Course instructors can create, rename, and archive text or voice channels.
- Learners and Cohort TAs can access only channels belonging to their exact Cohort.
- Archived channels disappear from navigation and cannot be messaged or joined.
- Channel names are normalized to slugs and punctuation-only names return `400`.

```sql
ALTER TABLE course_channels ADD COLUMN cohort_id UUID;
ALTER TABLE course_channels ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;

UPDATE course_channels cc
SET cohort_id = (
    SELECT c.id
    FROM cohorts c
    WHERE c.course_id = cc.course_id
);
```

```java
@Transactional
public CourseChannel createCohortChannel(UUID cohortId, UUID actorUserId, String name, ChannelKind kind) {
    requireCohortPeopleManager(cohortId, actorUserId, "Only a manager can create channels");
    courseRepository.lockCohortForChannelMutation(cohortId);
    if (courseRepository.activeChannelNameExists(cohortId, normalizeChannelName(name))) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "An active channel already uses that name");
    }
    // Allocate and insert while the Cohort lock is held.
}
```

### Exact authorization boundaries

- Navigation filters Course and channel projections before they reach the client.
- Direct message-access, support-question, voice-presence, and media-token reads enforce active channel plus exact Cohort access.
- A regression test proves that enrollment in another Cohort of the same Course does not grant Questions-channel access.

```sql
EXISTS (
    SELECT 1
    FROM cohort_enrollments ce
    WHERE ce.cohort_id = cc.cohort_id
      AND ce.learner_user_id = :userId
)
```

### Course voice through LiveKit

- Added a Community-owned Course voice-presence table keyed by channel and member with a 30-second `expires_at` lease.
- Added Course voice join/list/leave and media-token endpoints.
- Reused the existing LiveKit client with an explicit `course` scope.
- A media token no longer publishes presence. The client connects to LiveKit first, confirms presence second, and renews it every 10 seconds; failed joins, leaves, channel changes, and unmounts disconnect media and perform best-effort cleanup.
- Expired rows are filtered from reads, and a user whose enrollment was revoked can still delete their own stale presence.
- The selected Course voice channel now shows real profiles, connection state, mute/unmute, and leave controls.

```ts
const voice = useVoiceChannel(channel.id, 'course')

const connected = await liveKit.connect(token)
if (connected) await joinVoiceChannel(channel.id, 'course')
```

### Truthful v2 Chat workspace

- The selected channel comes from the real Cohort navigation response and URL `channel` query parameter.
- Durable history and realtime messages use the existing Message and Realtime services; sender IDs resolve through public profile reads.
- Owner-only create, rename, and archive dialogs replace dead controls.
- Attachment and emoji controls stay visibly disabled with an accessible unavailable explanation.
- Loading, empty, reconnecting, error, and no-channel states are explicit; no synthetic channel or message IDs are used.
- Conversation state is keyed by scope, channel, and user so switching channels clears stale content immediately and cannot send over the previous realtime client.
- Initial history and realtime subscription now reconcile after both sides are ready, closing the snapshot/subscription message-loss window.
- Voice loading and authorization failures are distinct from a genuinely empty room.
- Channel dialogs trap focus, close with Escape, make the workspace inert, and restore focus to the exact trigger after Chrome removes `inert`.
- The Course Overview Study Room action deep-links to the real Cohort voice channel.

```ts
const conversationKey = channelScope && channelId
  ? `${channelScope}:${channelId}:${userId ?? 'anonymous'}`
  : null

const currentState = conversationState.key === conversationKey
  ? conversationState
  : createConversationState(conversationKey, canConnect)
```

### Responsive and local-stack reliability

- Renamed the v2 Course channel-list class to prevent the legacy Dev Demo stylesheet from leaking gray/purple styles into the production route.
- Constrained the desktop Chat grid to the available workspace height so message history scrolls and the composer remains visible.
- Made the channel rail independently scrollable so long Course channel lists remain reachable at short viewport heights.
- Preserved the stacked mobile layout and verified no document-level horizontal overflow at `390x844`.
- `product_load_env` now supplies documented local LiveKit defaults when an existing `.env` predates those variables; its regression test uses an isolated environment fixture through `CHANTER_PRODUCT_ENV_FILE`.

```bash
export LIVEKIT_URL="${LIVEKIT_URL:-ws://localhost:7880}"
export LIVEKIT_HTTP_URL="${LIVEKIT_HTTP_URL:-http://localhost:7880}"
export LIVEKIT_API_KEY="${LIVEKIT_API_KEY:-devkey}"
export LIVEKIT_API_SECRET="${LIVEKIT_API_SECRET:-secret}"
```

## TDD coverage

Red/green cycles cover:

- manager create, rename, archive, invalid names, and learner denial;
- active-name conflict, archived-name reuse, same names across Cohorts, and concurrent position allocation;
- V11 migration success for a one-Cohort Course and deliberate failure for ambiguous legacy data;
- archived-channel message denial and exact Cohort navigation filtering;
- Course voice token-before-presence denial, join, lease expiry, revoked-member cleanup, leave, and outsider denial;
- direct Questions-channel denial for a learner enrolled only in another Cohort;
- frontend channel-management API contracts and Course voice endpoint paths;
- immediate stale-message removal, realtime-client keying on channel switch, and connect-before-history reconciliation;
- LiveKit-before-presence ordering, failed-publication disconnect, fail-safe leave, stale-join cancellation, and unmount cleanup;
- real channel/profile rendering, manager-only controls, and truthful disabled affordances;
- voice loading/error states and keyboard-modal focus behavior;
- Overview Study Room deep-linking;
- stale local `.env` LiveKit defaults.

## Live browser verification

The full local stack ran through Gateway with PostgreSQL, Redis, Redpanda, MinIO, LiveKit, all backend services, and Vite. Two registered users were created and enrolled through public APIs.

| View / action | Result |
|---|---|
| Owner and learner sign-in | Both signed in through the production auth UI |
| Cohort text channel | Durable history loaded with registered profile names |
| Realtime chat | Bidirectional messages arrived without reload |
| Channel management | Owner created, renamed, and archived a channel; learner never received management controls |
| Archive propagation | Archived channel disappeared for both users after navigation refresh |
| Course voice | Both users joined LiveKit, saw each other's real presence, and used mute/unmute |
| Presence heartbeat | Both users remained visible beyond the 30-second presence TTL |
| Voice cleanup | Both users explicitly left and disappeared from active presence |
| Long channel rail | A 17-row mixed channel list scrolled independently to its final row |
| Dialog keyboard flow | Initial focus, Escape close, inert background, and trigger restoration passed in Chrome |
| Desktop `1440x900` | Chat history scrolled inside the panel and composer remained visible |
| Mobile `390x844` | Stacked channel/chat layout rendered without document overflow |
| Runtime diagnostics | Zero browser console errors and zero API responses at `400+` |

Screenshots are local runtime artifacts under `.product/screenshots/` and are intentionally gitignored.

## Verification

```text
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
cd frontend && npm run lint
cd frontend && npm test -- --run
cd frontend && npm run build
bash scripts/product/lib.test.sh
make product-health
```

Final local results before commit:

- Full Java reactor: all 11 modules passed on Java 21.
- Community Service suite passed with all #137 smoke coverage.
- Frontend: 47 test files and 157 tests passed.
- Frontend lint and production build passed; the existing informational large-chunk warning remains.
- A disposable PostgreSQL 16 database passed the V11-to-V13 migration rehearsal, including constraint removal and the non-null voice lease.
- The two-user desktop/mobile/voice browser smoke passed with no console or API errors.
- The original local Community database was backed up, repaired from the earlier uncommitted migration drafts without deleting data, validated at all 14 migrations, and restored as the healthy running database.

## Related docs

- Debugging details: `docs/operations/issue-137-debug-log.md`
- Durable ownership model: `System Design.md`
- Progress tracking: `HANDOFF.md`, `plan.md`, and `docs/operations/agent-workflow.md`
