# Issue 14 Greptile Fix Log: Join A Voice Channel

Date: 2026-06-17  
Branch: `feature/14-join-voice-channel`  
PR: https://github.com/Vinosaamaa/chanter/pull/29  
Final Greptile confidence: `4/5`

## Summary

Greptile reviewed the Voice Channel presence slice and initially scored it `3/5`. Actionable feedback covered cross-member leave authorization, non-persisted capability flags, null `studyServerId` on in-memory channels, concurrent join upsert races, and PostgreSQL transaction-abort behavior on duplicate-key fallback. Iterations 1–5 addressed the concrete defects and aligned caller-identity handling with the existing MVP `TODO(#auth)` pattern used in Study Server creation.

## Fixes Applied

### 1. Voice Presence Upsert Race And PostgreSQL Transaction Safety

Greptile finding:

- `saveVoicePresence` used delete-then-insert, which could race under concurrent joins.
- A later insert-or-catch-update fallback aborted PostgreSQL transactions on duplicate key violations.

Fix:

- Branch upsert SQL by database product: `ON CONFLICT … DO UPDATE` on PostgreSQL and H2 `MERGE INTO … KEY` in tests.
- Detect PostgreSQL lazily on first voice-presence write instead of opening a connection at bean construction time.
- Added a smoke-test assertion for idempotent re-join.

Representative repository snippet:

```java
if (usePostgresUpsert()) {
    jdbcClient.sql("""
                    INSERT INTO voice_channel_presences (channel_id, member_user_id, joined_at)
                    VALUES (:channelId, :memberUserId, :joinedAt)
                    ON CONFLICT (channel_id, member_user_id)
                    DO UPDATE SET joined_at = EXCLUDED.joined_at
                    """)
            ...
            .update();
} else {
    jdbcClient.sql("""
                    MERGE INTO voice_channel_presences (channel_id, member_user_id, joined_at)
                    KEY (channel_id, member_user_id)
                    VALUES (:channelId, :memberUserId, :joinedAt)
                    """)
            ...
            .update();
}
```

### 2. Default Channels Now Carry `studyServerId`

Greptile finding:

- `createStudyServer` used the legacy 4-arg `StudyServerChannel` constructor, leaving `studyServerId == null` on in-memory channel objects.

Fix:

- Generate `studyServerId` once and pass it into all default channel constructors.
- Document the legacy constructor as test-only.

### 3. Deferred Capability Flags Documented

Greptile finding:

- `canSpeak` / `canListen` were exposed in API responses without schema backing.

Fix:

- Added explicit comments in the repository and migration noting capability columns ship with a later media slice.
- Left flags hardcoded `true` for the presence-only MVP.

### 4. Caller Identity Matches Existing MVP Auth Posture

Greptile finding:

- An interim `actingUserId == memberUserId` guard compared two caller-supplied parameters and was still bypassable without an authenticated principal.

Fix:

- Removed the misleading dual-parameter guard.
- Switched leave to the same request-body `memberUserId` shape as join.
- Added `TODO(#auth)` on `CreateVoicePresenceRequest`, consistent with `CreateStudyServerRequest`.

Representative request snippet:

```java
public record CreateVoicePresenceRequest(
        // TODO(#auth): replace caller-supplied member ids with the authenticated principal.
        @NotNull UUID memberUserId
) {
}
```

Unresolved follow-up:

- Real caller verification requires the Auth Service principal slice (`#auth`). Until then, join/leave/list identity parameters remain request-supplied across this vertical slice, matching issues #12–#13.

## Verification

- `JAVA_HOME=... mvn -pl community-service -Dtest=VoiceChannelPresenceSmokeTest test`
- `JAVA_HOME=... mvn verify`
- `npm run lint`
- `npm run build`
- PR #29 CI backend/frontend passed on iterations 1–4

## Final Result

- Greptile initial summary: `3/5`
- Greptile after iterations 1–4: `4/5`, 6 review threads resolved
- Greptile after iteration 5: `4/5`, 0 unresolved review threads
- Remaining Greptile concern: caller identity is request-supplied until `#auth` lands (acknowledged `TODO(#auth)`, consistent with issues #12–#13)
- Greploop iterations: 5 (max)
