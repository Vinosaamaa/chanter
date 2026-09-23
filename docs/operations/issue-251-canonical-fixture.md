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
| media-read-fixture | media | resourceId | base64 of only the exact fixed fixture bytes after current AVAILABLE/settled/size/SHA verification through configured private storage |
| resource-delete | media | resourceId, ownerId | real pending SourceDeletionRequests result |
| events | any of these three | afterRevision nonnegative integer | up to 64 original lifecycle destination/event pairs, ordered by revision |
| deliver | any of these three | original event object | eventId and committed=true after the real owning consumer returns |
| journal | auth | afterRevision | actual committed TerminalJournal.Page, at most 64 entries |
| current-scope | community | original entry, kind COURSE/CHANNEL, afterId UUID | actual immutable DeletedScope.Page, at most 256 IDs |

Use a separate account without owned servers for ACCOUNT deletion, and a second owner for RESOURCE/STUDY_SERVER. Preparation must deliver the actual auth event to community and its actual receipt back to auth before confirmation. Source deletion must deliver its actual generated request to auth, then the actual auth terminal event back to the owner. No fixture action can append a journal row or manufacture ownership approval. `events` reads committed rows without marking transport completion; the runner must track its cursor, preserve event identity and permit deliberate duplicate delivery. It is explicit test relay, not production dispatcher proof.

Use the zero UUID for the first current-scope cursor. Follow returned page cursors and require READY/count/digest agreement. Read journal pages after owning confirmation/delivery has committed. The runner must retain both COURSE and CHANNEL pages for each server before checkpoint acknowledgement. Canonical source generation is distinct from synthetic `TerminalDeletionTestSupport` entries in unit tests.

`media-upload` sends fixed UTF-8 `fixture.txt` bytes through CourseResourceService with AI approval disabled. Its real current course/owner/moderation checks, validator and configured private storage remain in effect. The fixture must assert QUARANTINED, then invoke `media-work-once` with the real configured scanner. Assert actual AVAILABLE and storageWriteSettled=true before archiving bytes. Run these actions on a fresh isolated fixture queue before adding negative rows; a one-shot worker call does not promise that an arbitrary requested job won the queue. Failed scan, unavailable scanner or another queued job must not be reported as successful availability. No scan verdict, storage key or file content is caller-supplied.

`media-read-fixture` exposes no general byte reader. It requires the fixed text's exact size and SHA-256, current AVAILABLE state and settled physical write, then reads at most that small fixture length plus one byte from the configured storage adapter. Stored bytes must equal the fixed text before a base64 response is produced. The runner can encrypt those bytes without knowing container filesystem paths or placing private storage keys on argv. Other resources and substituted bytes fail closed.

`media-seed` still reserves a STAGING row without writing bytes. It must remain unsettled and cannot prove physical deletion or object restore. #342 owns the native real scanner/object execution. Historical restored-only child rows and a server created after the selected backup must be exercised by the full native orchestration; this helper checkpoint does not claim those journeys passed.

The focused auth test compiles this exact source and uses actual session issuance, preparation and outbox rows. It proves confirmation fails without community preparation and that tokens/receipt credentials are absent from output. Full PostgreSQL source relay and both-architecture packaged execution are still required.

## Account-authored content delivery checkpoint

The fixture's existing `deliver` action also accepts `ACCOUNT_CONTENT_ADVANCE` and `ACCOUNT_CONTENT_ERASED` on an owning community source. It preserves the actual event identity and invokes the same source consumer as the private lifecycle HTTP route. Source events retain their fixed destination; a helper commit is not acknowledgement from another service.

Community and message emit `ACCOUNT_CONTENT_ERASE` to `lifecycle-search` and `lifecycle-notification`. All commands use the existing `POST /api/v1/internal/lifecycle/events` route and internal service authentication. Each erase payload is `{entry,refs}`. `entry` is the original schema-2 ACCOUNT journal entry; `refs` contains 1–256 `{kind,id}` records ordered by kind and canonical lowercase UUID text. Community permits ANNOUNCEMENT, EVENT and OFFICE_HOURS; message permits MESSAGE, QUESTION, QUESTION_PREVIEW and FAQ. The aggregate is `ACCOUNT_CONTENT:<terminal-event-id>:<batch-id>` and remains unchanged in the receipt. The two destination commands have separate actual outbox event IDs.

Each recipient requires the exact applied local terminal entry before mutation. Its receipt payload is `{owner,commandId,batch}` where `commandId` is the original destination event ID and `batch` is its unchanged payload. The source checks every retained ID, the recorded destination event ID and aggregate before committing its acknowledgement. Marker insertion, payload removal and receipt append are transactional. Reordered independent batches and duplicate delivery are supported. A forged or incomplete acknowledgement cannot finish a batch.

The source advances at most 256 retained identities per existing outbox command. It removes matching ordinary search/notification payloads and changes those source outbox rows to `ERASED`, which cannot be reclaimed or manually replayed. Already-claimed copies remain covered by permanent recipient markers. No additional retry queue or scheduler is introduced.

This checkpoint deliberately leaves account cleanup PENDING. Explicit per-producer final disposition, including verified zero content, downstream completion aggregation, remaining agent/media disposition and the real PostgreSQL recovery union are still required. A batch receipt alone cannot authorize recovery cutover or a deletion-complete status. The journal, current/derived server scope and media V5 contracts are unchanged.
