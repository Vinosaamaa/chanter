# Issue #137 - debugging log

## Purpose

Record the failures found while operationalizing Course Chat and voice, how they were isolated, and the permanent regression coverage added for future agents.

## 1. Browser automation bootstrap failed

**Symptom**

The bundled Chrome-control bootstrap failed before attaching to a tab:

```text
Cannot redefine property: process
```

**Diagnosis**

The local plugin bootstrap attempted to assign `globalThis.process` inside a runtime where that property was already non-configurable. This happened before any Chanter page interaction.

**Resolution**

Used the workspace-provided Playwright runtime with a separate temporary Chrome profile. The fallback did not depend on the user's Chrome extension or personal browser session.

## 2. Voice presence appeared but LiveKit never connected

**Symptom**

Both users appeared in the Community voice-presence response, but the UI stayed on `Ready to join` and displayed:

```text
Failed to construct 'URL': Invalid URL
```

**Diagnosis**

- LiveKit health was green and media-token requests returned `200`.
- The workspace `.env` was older than `.env.example` and did not contain `LIVEKIT_URL`, `LIVEKIT_HTTP_URL`, `LIVEKIT_API_KEY`, or `LIVEKIT_API_SECRET`.
- Community Service therefore issued an unresolved/invalid server URL even though the product health check only verified the LiveKit container.

**Red test**

`scripts/product/lib.test.sh` loads an isolated stale environment containing only required Chanter secrets and expects the documented LiveKit defaults. This avoids accidentally passing because of the developer machine's `.env`.

**Fix**

`product_load_env` exports the same local defaults used by Docker Compose and `product_livekit_url`. After restart, two users connected, muted/unmuted, and left through LiveKit.

## 3. Same-Course enrollment leaked Questions access across Cohorts

**Symptom**

A learner enrolled only in the Winter Cohort could call the Summer Cohort's Questions access endpoint and received `200` with posting permission.

**Diagnosis**

`findSupportQuestionChannelAccess` checked whether the learner or TA belonged to any Cohort in the Course instead of matching `course_channels.cohort_id`.

**Red test**

`StudyServerNavigationSmokeTest` creates a second Cohort and learner, then requests the first Cohort's Questions channel. The assertion expected `403` and initially received `200`.

**Fix**

Enrollment and TA predicates now compare directly to the selected channel's `cohort_id`. The targeted test and full Community suite pass.

## 4. V2 channel list inherited legacy Dev Demo colors

**Symptom**

Browser screenshots showed the Course channel list with a translucent gray background, purple text, clipped labels, and narrower rows even though the v2 stylesheet declared transparent backgrounds.

**Diagnosis**

Browser-computed styles reported:

```text
listBackground: rgba(255, 255, 255, 0.55)
labelColor: rgb(124, 58, 237)
labelWidth: 16px
```

An unscoped legacy rule in `DevDemoApp.css` also used `.channel-list`. Its selector loaded globally and overrode the production v2 route.

**Fix**

- Renamed the v2 class to `.course-channel-list`.
- Replaced the fragile inner grid with a stable flex row and reserved action width.
- Final computed labels measure `160-170px`, rows fill the panel, and intended v2 colors are restored.

## 5. Long chat history hid the composer

**Symptom**

With repeated realtime messages, the desktop Chat panel grew below the viewport and the composer disappeared instead of the history scrolling.

**Diagnosis**

`.course-chat-layout` used only `min-height: 100%`, allowing content to expand its parent. The inner `overflow-y: auto` list had no bounded parent height.

**Fix**

Desktop Chat now uses the available workspace height with `min-height: 0` and hidden outer overflow. At `900px` and below, the layout returns to auto height so the stacked mobile view remains scrollable.

## 6. Headless Chrome produced intermittent black screenshot tiles

**Symptom**

Some otherwise functional screenshots contained black compositing rectangles.

**Diagnosis**

DOM metrics, computed colors, API traffic, and interaction assertions were all correct. The artifact varied between identical runs and affected system-Chrome headless compositing layers.

**Resolution**

The smoke uses software rendering, an sRGB color profile, disabled screenshot animations, and viewport captures. Final desktop, mobile, and voice frames rendered cleanly.

## 7. Legacy channel migration could silently choose the wrong Cohort

**Symptom**

The first V12 draft used `ORDER BY ... LIMIT 1`, so a legacy Course with multiple Cohorts would silently assign every channel to one arbitrary Cohort. The old `UNIQUE(course_id, name)` constraint also remained, preventing same-name channels across Cohorts and archived-name reuse.

**Fix**

- The backfill now uses a strict scalar subquery. Exactly one Cohort succeeds; multiple Cohorts or an orphan fail migration loudly.
- `V12_1__drop_legacy_course_channel_name_constraint` discovers the exact legacy unique constraint through `information_schema` and removes it on both H2 and PostgreSQL.
- Application-level active-name checks run while holding a `SELECT ... FOR UPDATE` lock on the Cohort row.

**Proof**

An automated V11 upgrade test covers valid and ambiguous data. A disposable PostgreSQL 16 rehearsal confirmed the backfill, constraint removal, cross-Cohort name reuse, and final V13 schema.

## 8. Media-token requests created phantom voice members

**Symptom**

The first implementation wrote Community presence while issuing a LiveKit token. A failed media connection, browser crash, or abandoned join could therefore leave a member visible indefinitely.

**Fix**

- Token issuance is authorization-only and does not write presence.
- The client confirms presence only after `Room.connect` succeeds.
- Presence expires after 30 seconds and connected clients renew every 10 seconds.
- Failed publication disconnects LiveKit; leave disconnects media before the API request; channel changes and unmounts perform best-effort cleanup.
- Presence deletion no longer requires current enrollment, allowing a revoked user to clean up their own row.

**Proof**

Backend tests cover token-without-presence, expiry, and revoked-user cleanup. Hook tests cover ordering, failed publication, failed leave, stale channel joins, and unmount. The two-user browser smoke held both users beyond the TTL and then removed both cleanly.

## 9. Initial realtime startup could miss a message

**Symptom**

History loading and WebSocket connection started concurrently. If the socket subscribed before history returned, the connected callback had no cursor and never reconciled the gap after the history snapshot.

**Fix**

The conversation hook tracks history readiness and realtime readiness separately. Once both are ready it always reconciles from the history cursor, then uses the latest monotonic cursor for subsequent reconnects.

## 10. Channel creation could return the wrong resource URL or duplicate positions

**Symptom**

The create response pointed to the unmapped nested URL `/cohorts/{id}/channels/{id}`, and separate `MAX(position)` and insert transactions allowed concurrent callers to choose the same position.

**Fix**

The canonical `Location` is `/api/v1/course-channels/{id}`. Channel creation now runs in one transaction under the Cohort row lock; a two-thread regression test receives positions `4` and `5`.

## 11. Chrome did not restore focus after closing a channel dialog

**Symptom**

The JSDOM keyboard test passed, but Chrome left focus on the document body after Escape. Applying `inert` to the workspace had already blurred the trigger before the modal effect captured `document.activeElement`.

**Fix**

The page records the active trigger in the click handler before opening the modal. On close, it removes the modal and restores that exact element on the next animation frame, after React removes `inert`. The final Chrome smoke verifies initial focus, Escape, and trigger restoration.

## 12. Local migration drafts had already touched PostgreSQL

**Symptom**

The local `chanter_community` database had run earlier uncommitted versions of V12 and V13, so the finalized checksums and added V12.1 migration could not be validated by a normal restart.

**Resolution**

- Backed up the database to `.product/backups/chanter_community_before_issue137_repair.sql`.
- Added and backfilled `expires_at` without deleting data.
- Ran Flyway repair for the finalized V12/V13 checksums and applied V12.1 out of order locally.
- Restarted the normal Community service and confirmed all 14 migrations validate with the original data intact.

## Final proof

```text
owner and learner signed in through the browser
Cohort channel loaded with manager controls owner-only and truthful composer controls
bidirectional realtime chat delivered with registered profile names
owner created and renamed a durable Cohort text channel visible to learner
archived channel disappeared for owner and learner
two users joined Course LiveKit voice with presence and mute control
both users remained present beyond the 30-second lease through heartbeat renewal
both users left Course voice cleanly
long desktop channel list scrolled to its final row
channel dialog restored focus in Chrome after Escape
390x844 mobile Chat rendered without horizontal overflow
consoleErrors: []
responseErrors: []
```
