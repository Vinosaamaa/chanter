# Canonical deletion fixture

`scripts/deploy/fixtures/CanonicalLifecycleFixture.java` is unshipped hosted test code. Compile it with the packaged service classes/dependencies, then invoke its fixed class name with no arguments. Set `CHANTER_CANONICAL_LIFECYCLE_FIXTURE=true` only in the isolated fixture. The packaged `/app/main-class` selects auth, community or media; the helper rejects other services and recovery mode. One bounded JSON request arrives on stdin, one JSON result goes to stdout, and application logs go to stderr. Input is limited to 64 KiB and output to 1 MiB. It exposes no HTTP route.

Run this normal-source preparation on an isolated service network with real auth/community authority clients. Do not substitute permissive access clients. Email delivery and ordinary outbox/media/ingestion dispatch are disabled for deterministic explicit relay. Synthetic auth registration additionally requires email-verification-disabled fixture configuration; production configuration is not changed. Session and receipt credentials never appear in results.

Fixed actions and exact input fields:

| Action | Source | Fields beyond action | Result |
| --- | --- | --- | --- |
| auth-seed | auth | alias UUID | alias and real accountId |
| account-prepare | auth | alias, jobId UUIDs | real prepared Job without receipt credential |
| account-confirm | auth | alias, jobId | real Job after recent login and owning confirmation |
| community-seed | community | ownerId | generated serverId, courseId and channel records |
| server-delete | community | serverId, ownerId | real pending SourceDeletionRequests result |
| media-seed | media | resourceId, courseId, serverId, ownerId | reserved synthetic metadata, storageWriteSettled=false |
| media-upload | media | courseId, ownerId, requestId UUIDs | actual upload resourceId/courseId/storageKey/backend/byteSize/sha256/state/storageWriteSettled |
| media-work-once | media | resourceId | same owning metadata after one real worker operation, requiring QUARANTINED before invocation |
| resource-delete | media | resourceId, ownerId | real pending SourceDeletionRequests result |
| events | any of these three | afterRevision nonnegative integer | up to 64 original lifecycle destination/event pairs, ordered by revision |
| deliver | any of these three | original event object | eventId and committed=true after the real owning consumer returns |
| journal | auth | afterRevision | actual committed TerminalJournal.Page, at most 64 entries |
| current-scope | community | original entry, kind COURSE/CHANNEL, afterId UUID | actual immutable DeletedScope.Page, at most 256 IDs |

Use a separate account without owned servers for ACCOUNT deletion, and a second owner for RESOURCE/STUDY_SERVER. Preparation must deliver the actual auth event to community and its actual receipt back to auth before confirmation. Source deletion must deliver its actual generated request to auth, then the actual auth terminal event back to the owner. No fixture action can append a journal row or manufacture ownership approval. `events` reads committed rows without marking transport completion; the runner must track its cursor, preserve event identity and permit deliberate duplicate delivery. It is explicit test relay, not production dispatcher proof.

Use the zero UUID for the first current-scope cursor. Follow returned page cursors and require READY/count/digest agreement. Read journal pages after owning confirmation/delivery has committed. The runner must retain both COURSE and CHANNEL pages for each server before checkpoint acknowledgement. Canonical source generation is distinct from synthetic `TerminalDeletionTestSupport` entries in unit tests.

`media-upload` sends fixed UTF-8 `fixture.txt` bytes through CourseResourceService with AI approval disabled. Its real current course/owner/moderation checks, validator and configured private storage remain in effect. The fixture must assert QUARANTINED, then invoke `media-work-once` with the real configured scanner. Assert actual AVAILABLE and storageWriteSettled=true before archiving bytes. Run these actions on a fresh isolated fixture queue before adding negative rows; a one-shot worker call does not promise that an arbitrary requested job won the queue. Failed scan, unavailable scanner or another queued job must not be reported as successful availability. No scan verdict, storage key or file content is caller-supplied.

`media-seed` still reserves a STAGING row without writing bytes. It must remain unsettled and cannot prove physical deletion or object restore. #342 owns the native real scanner/object execution. Historical restored-only child rows and a server created after the selected backup must be exercised by the full native orchestration; this helper checkpoint does not claim those journeys passed.

The focused auth test compiles this exact source and uses actual session issuance, preparation and outbox rows. It proves confirmation fails without community preparation and that tokens/receipt credentials are absent from output. Full PostgreSQL source relay and both-architecture packaged execution are still required.
