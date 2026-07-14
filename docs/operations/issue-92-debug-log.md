# Issue #92 - debug log

## Purpose

Record meaningful implementation and verification failures encountered while operationalizing Questions, support, and Teaching, including how each failure was isolated.

## 1. Reply endpoint began as a failing public contract

**Symptom:** The first `POST .../support-questions/{id}/replies` MockMvc assertion returned `404`.

**Diagnosis:** Message Service had durable Support Questions but no human-reply resource or persistence model.

**Fix:** Added the reply migration, domain/repository/API contracts, staff authorization, ordered reads, and `HUMAN_ANSWERED` transition.

**Proof:** Reply create/read, learner rejection, author visibility, and closed-question tests pass.

## 2. TA Queue and FAQ calls returned 403 after removing client actor IDs

**Symptom:** Authenticated browser/API calls to TA Queue and FAQ routes returned `403` while the same role checks passed in direct service tests.

**Diagnosis:** The Message Service `AuthenticatedUserFilter` did not classify those paths as authenticated, so no principal request attribute reached the controllers.

**Fix:** Added TA Queue and Approved FAQ paths to the filter and changed all public operations to derive actor identity from the authenticated request.

**Proof:** Principal-derived TA Queue and FAQ MockMvc tests pass through the web boundary.

## 3. Live Ask AI returned 502 for a stale local resource

**Symptom:** The first live `POST .../assistant-answer` returned `502` even though Gateway, Agent Service, and Media Service were healthy.

**Diagnosis:** PostgreSQL still contained three AI-approved resource rows and Agent grants, but the currently launched Media Service resolved its relative storage directory from `backend/data/course-resources`; the bytes existed under an older `backend/media-service/data/course-resources` working directory. Media returned `500`, its client mapped that to `BAD_GATEWAY`, and Agent propagated it.

**Fix:** Added a red test for an unavailable granted resource, then made resource-level `404`, `403`, and `502` failures skippable so grounding continues and produces a durable low-confidence handoff when no source remains.

**Proof:** The same live request changed from `502` to `200` with a persisted `AI_LOW_CONFIDENCE` answer. For citation browser QA only, the ignored local fixture bytes were restored to the active storage directory; no runtime data was committed.

**Follow-up ownership:** Stable object-storage ingestion and local storage lifecycle belong to #94 and product reliability work; #92 prevents that partial failure from breaking the support path.

## 4. Browser plugin runtime could not initialize

**Symptom:** The desktop browser bridge failed twice before page selection with `Cannot redefine property: process`.

**Diagnosis:** Product health remained green and no Chanter request was made, isolating the failure to the desktop browser-control runtime.

**Response:** Followed the browser-control fallback sequence. macOS Computer Use was attempted next, then a separate headless Chrome was launched locally and controlled through the Chrome DevTools Protocol for screenshots, DOM interaction, and viewport checks.

## 5. macOS Computer Use fallback was locked

**Symptom:** Accessibility automation reported that the Mac was locked and automatic unlock was unavailable.

**Diagnosis:** The failure occurred before Chrome could be read and did not affect Chanter services.

**Response:** Continued with local headless Chrome rather than claiming the owner-visible system Chrome was controlled.

## 6. Browser QA exposed filter/thread selection drift

**Symptom:** On first load, the Open list showed an older question while the thread displayed a newer resolved question with the same text. After Ask AI, the newly answered question disappeared from the active filter and another thread became selected.

**Diagnosis:** The hook selected the newest durable question independently of the page filter, and status mutations could move the selected question between filter groups.

**Fix:** Added failing component tests, made user filter changes select within that group, and derived the active filter from the selected question when a mutation changes its status.

**Proof:** The live learner remained on the AI answer and citation after Ask AI; Open/Answered labels, list highlight, and thread stayed aligned.

## 7. Resolved TA item left the adjacent thread stale

**Symptom:** Queue resolution emptied the TA Queue, but the visible question still showed `Staff answered` until a manual refresh, while the backend already returned `RESOLVED`.

**Diagnosis:** The TA Queue hook refreshed only its own state; the Questions hook had no cross-panel invalidation.

**Fix:** Added a failing page test and refresh the Questions query after an in-progress TA item resolves.

**Proof:** The regression test passes and the live refresh returned the final durable `RESOLVED` state with the staff reply intact.

## 8. Docker CLI was installed but absent from the shell PATH

**Symptom:** Product commands initially could not find `docker`.

**Diagnosis:** Docker Desktop was installed at `/Applications/Docker.app`, but its resources directory was not in the agent shell PATH.

**Response:** Started and verified the stack with `/Applications/Docker.app/Contents/Resources/bin` added to PATH. No repository workaround was needed.

## 9. Review found cross-Course TA Queue injection

**Symptom:** A learner could submit a low-confidence question from one Course to a Cohort Queue owned by another Course.

**Diagnosis:** Queue creation authorized the learner against both resources independently but did not compare their Course IDs.

**Fix:** Added a failing MockMvc test and require the question channel Course ID to equal the Cohort Course ID before insertion.

**Proof:** The cross-Course request now returns `400`; all five TA Queue smoke tests pass.

## 10. Review found dashboard identity spoofing and partial-instructor failure

**Symptom:** The Message Service metrics endpoint trusted a `viewerUserId` in its JSON body. Separately, a Course instructor could receive a server-wide grant-candidate list and then fail the entire Teaching page when Message Service rejected another instructor's Course.

**Diagnosis:** The metrics route was omitted from authenticated filtering, and Analytics aggregated the unfiltered server-wide Course list in one request.

**Fix:** Removed body identity, required `X-User-Id` through the authenticated request attribute, forwarded the principal in the internal Analytics client header, and aggregate only per-Course metrics that authorize for the viewer.

**Proof:** A body spoof test changed from `200` to `403`; a partial-instructor unit test renders only the permitted Course and its totals.

## 11. Review found terminal-state and Queue consistency races

**Symptom:** Directly resolving, cancelling, or marking a question duplicate left an active Queue item. A reply could also race a terminal moderation after the question had already reached `HUMAN_ANSWERED`.

**Diagnosis:** Direct moderation updated only the question, and repeat replies skipped the compare-and-set row lock for `HUMAN_ANSWERED`.

**Fix:** Close active Queue rows in the same transaction as direct moderation and always acquire the question status row with a compare-and-set update before saving a reply.

**Proof:** A picked-up Queue item disappears after direct duplicate moderation; reply and moderation smoke tests remain green.

## 12. Review found frontend stale-state races

**Symptom:** A draft could follow the user to another thread, an older reply read could erase a just-posted reply, and the previous Cohort Queue stayed actionable while the next Cohort loaded.

**Diagnosis:** Draft state had no thread/version boundary, reply reads replaced local state wholesale, and Queue output was gated only on the presence of a request key.

**Fix:** Versioned and cleared thread drafts, merged reply reads by ID with local replies winning, and expose Queue data only when `loadedKey` exactly matches the active request.

**Proof:** Focused hook/component regressions pass, and the full frontend suite passes 135 tests across 40 files.

## 13. Review found citation and Office Hours query parameters were ignored

**Symptom:** Questions generated `?resource=<id>` links and Teaching generated `?session=<id>` links, but the destination pages opened their default list state.

**Diagnosis:** The destination pages never consumed those query parameters.

**Fix:** Resource rows and scheduled Office Hours rows now resolve the requested ID, expose `aria-current`, and receive a visible selected treatment. The selected Office Hours session also drives the next-session summary.

**Proof:** Component tests cover both deep links. Live browser QA opened the real `Homework Help Guide` citation by ID and highlighted its row without horizontal overflow.

## 14. First PR backend run hit an unrelated realtime test timeout

**Symptom:** PR #151's first backend job failed after all #92 services passed because `SocialRealtimeWebSocketSmokeTest.friendPresenceAndDirectMessagesFanOutOverWebSocket` timed out after 30 seconds while sending the initial friend-presence snapshot.

**Diagnosis:** #92 does not modify `realtime-service`. The exact failing class passed locally on Java 21 with both tests completing in 2.8 seconds, which identifies the GitHub runner result as a transient WebSocket timing failure rather than a #92 regression.

**Response:** Requested a failed-job rerun through GitHub, but the repository credential did not have Actions-write permission (`403`). Amended the existing single issue commit to trigger a fresh workflow run while preserving one commit for #92.
