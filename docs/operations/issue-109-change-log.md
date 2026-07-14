# Issue #109 - change log

**Branch:** `feature/109-real-v2-friend-requests`
**Commit:** `feat(friends): #109 make v2 friend requests and discovery real`

## Goal

Replace fixture-driven Friends UI v2 behavior with privacy-scoped co-member discovery, real profile names, durable Friend Request actions, accepted Friends, presence, exact Direct Message history, and honest unsupported-control states.

## What changed

### Privacy-scoped co-member discovery

- Community Service now returns only users who share at least one canonical Study Server membership with the signed-in viewer.
- Membership includes Study Server roles, Course roles, Cohort enrollment, and Cohort roles.
- The response exposes a peer ID and one shared Study Server name; it does not expose strangers or profile data.
- Gateway routing now includes `/api/v1/social/**`.

```java
@GetMapping("/social/co-members")
public CoMemberListResponse findCoMembers(
        @RequestAttribute(AuthRequestAttributes.USER_ID) UUID userId
) {
    return CoMemberListResponse.from(socialMembershipService.findCoMembers(userId));
}
```

### Bounded public profile lookup

- Auth Service adds an authenticated batch lookup for at most 100 user IDs.
- The frontend batches larger directories into 100-ID requests and combines the ordered results.
- The response contains only `userId` and `displayName`; email and unknown accounts are omitted.
- Request order is preserved and duplicate IDs are collapsed.
- This is an interim identity read boundary until profile ownership moves to a dedicated User Service.

```java
public record PublicProfileQueryRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> userIds
) {
}
```

### Truthful relationship state

- Message Service exposes the signed-in viewer's blocked user IDs.
- The frontend combines co-members, public profiles, Friends, incoming requests, outgoing requests, and blocks into one deterministic directory.
- State precedence is `blocked`, `friend`, `incoming`, `outgoing`, then `available`.
- Sending, accepting, declining, cancelling, and blocking use existing durable APIs and invalidate the shared Friend Request cache.
- Blocking requires an explicit confirmation because no unblock UI exists yet.

```ts
if (blockedUserIds.has(coMember.userId)) state = 'blocked'
else if (friendUserIds.has(coMember.userId)) state = 'friend'
else if (incomingUserIds.has(coMember.userId)) state = 'incoming'
else if (outgoingUserIds.has(coMember.userId)) state = 'outgoing'
```

### Operational Friends and DM page

- Removed demo Friends, pending requests, names, and message fixtures.
- The pending badge counts real incoming requests only.
- Friend Request queries refresh every 15 seconds so incoming badges update without a page reload while request-specific realtime events remain out of scope.
- Accepted Friends render real display names and presence, and selecting a Friend loads that exact peer's durable DM history.
- Voice calling continues through the existing realtime and LiveKit path.
- Video, attachment, and emoji controls are explicitly disabled with accessible explanations because those capabilities do not exist yet.
- The Add Friend dialog searches only co-members and supports initial focus, focus containment, and Escape dismissal.

```tsx
{entry.state === 'available' ? (
  <button onClick={() => void relationships.sendRequest(entry.userId)}>
    Send
  </button>
) : (
  <b className={`relationship-state ${entry.state}`}>
    {relationshipStateLabel(entry.state)}
  </b>
)}
```

### Cross-account query isolation

- Browser testing exposed a stale Study Server sidebar immediately after switching accounts.
- Authenticated TanStack Query data is now cleared whenever the authenticated user ID changes or signs out.
- Study Server and navigation query keys also include the user ID so access and capability data cannot be reused across identities.
- Token refreshes for the same user retain the cache.

```tsx
useEffect(() => {
  if (previousUserId.current === userId) return
  queryClient.clear()
  previousUserId.current = userId
}, [queryClient, userId])
```

## TDD coverage

Red/green coverage includes:

- co-member discovery includes a shared learner and excludes a stranger;
- authenticated public profile lookup reveals display name but not email;
- block-list lookup returns only the viewer's blocks;
- frontend API paths and request bodies;
- profile lookup batching beyond the 100-ID API limit;
- deterministic co-member relationship states;
- real names, exact DM context, unsupported-control states, co-member send, and pending actions;
- Add Friend focus and Escape behavior;
- cache retention for same-user token refresh and cache clearing on account change/sign-out.

## Browser and service verification

Two fresh accounts were registered through the gateway and enrolled in one shared Course without an existing friendship.

| Step | Browser proof |
|---|---|
| Owner opens Add Friend | Exactly `Issue 109 Peer` appears under `Issue 109 Shared Server` |
| Owner sends request | Candidate changes to `Pending`; outgoing list shows one real request |
| Peer signs in | Sidebar shows only the shared server; incoming badge is `1` |
| Peer accepts | Badge becomes `0`; `Issue 109 Owner` becomes the selected accepted Friend |
| Peer sends DM | `Browser QA hello from Issue 109 Peer` appears in the thread |
| Owner signs back in | Accepted Friend and the exact persisted DM appear |
| Post-fix account switch | No previous-user Study Servers flash or remain in the sidebar |

`make product-health` passed before the flow. `make product-down` stopped the app processes and supervisor after browser verification.

## Architecture and docs

- Updated `docs/architecture/social-hub-and-dm-voice.md` from planned state to the implemented #31/#32/#109 boundaries.
- Updated `System Design.md` with the current interim profile/social ownership transition.
- Updated `HANDOFF.md`, `plan.md`, and `docs/operations/agent-workflow.md` to mark #134 merged and #109 locally complete, with #135 next after the PR gate.
- Debug details are in `docs/operations/issue-109-debug-log.md`.

## Verification

```text
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
cd frontend && npm run lint
cd frontend && npm run test -- --run
cd frontend && npm run build
git diff --check
make product-health
```

Backend reactor: 11 modules passed. Frontend: 33 test files and 110 tests passed. The Vite build retains the existing informational large-chunk warning.
