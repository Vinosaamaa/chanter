# Issue #246 implementation and validation

## Independent parser and ingestion slice

Added bounded text/Markdown/PDF/DOCX/PPTX extraction with explicit outcomes, normalized source text, locator metadata and source/parser checksums. Added generation-fenced atomic index publication, same-source replay, safe failure states and deletion that does not wait for embedding preparation. Current evidence validation uses the same extractor, including rejection of incomplete scans.

## TDD evidence

- Extraction fixture tests first failed because the document extraction contract did not exist. Real format fixtures then passed. Additional graphical-PDF and image-only Office regressions failed as EMPTY before the OCR-required classification fix.
- Deletion during paused first ingestion and direct backfill initially timed out. A newer ingestion contended behind the older provider call, and identical re-ingestion replaced chunk IDs. Generation-fenced final transactions now pass those cases while retaining terminal deletion and nonterminal migration purge coverage.
- Normalized evidence validation initially rejected the NFC excerpt of an NFD source. Shared normalized extraction now validates current text/PDF evidence and rejects a mixed scanned PDF rather than accepting its partial text.
- Locator citation construction initially lacked the additive metadata contract. The compatible constructor and page-bearing citation title now pass.

Commands use the repository Maven settings and Java 21:

```sh
mvn -o -B -s backend/.mvn/settings.xml -f backend/pom.xml -pl agent-service -am verify
mvn -o -B -s backend/.mvn/settings.xml -f backend/pom.xml -pl agent-service -am -Dtest=ResourceIndexDeletionFenceTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -o -B -s backend/.mvn/settings.xml -f backend/pom.xml -pl agent-service -am -Dtest=DocumentExtractionTest,AiEvidenceAuthorizationTest,RagGroundingEngineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Full local agent verification passed after the generation and normalization changes. Native PostgreSQL, package security, full review, durable-event integration and instructor browser acceptance remain pending; local fixture success is not issue completion.

## Instructor lifecycle integration

The media client forwards document formats and preserves explicit extraction outcomes/signals. Successful downloads remain independent of AI readiness. Expired processing leases recover, stale leases cannot publish, and only an instructor can retry FAILED preparation. Existing COMPLETE rows are requeued because that old state included formats skipped without extraction. DOCX validation checks its container while retaining byte limits and macro rejection.

The resource page shows queued, processing, ready and actionable source-failure descriptions; it disables unavailable downloads and exposes retry only to instructors. Pending states refresh in place. A retry response from the previous course is ignored after navigation. Upload help explicitly excludes scanned text, audio and video from readable AI content. Current catalog authorization requires READY as well as AVAILABLE/approval/course/viewer permission.

Affected media+agent verification passed after the outcome changes. The added READY catalog HTTP test first failed by returning unready rows, then passed after filtering them. Sixteen focused resource-page/hook tests pass. Lint, all 285 frontend tests and the production frontend build pass within the existing CSS budget after reusing shared status styles. A first combined local Vitest attempt failed to start its fork workers; the bounded one-worker run passed. Real browser acceptance remains in progress.

```sh
mvn -o -B -s backend/.mvn/settings.xml -f backend/pom.xml -pl media-service,agent-service -am verify
npm run lint
npm run build
npm test -- --maxWorkers=1 --run src/features/v2-shell/pages/course/CourseResourcesPage.test.tsx src/features/shell/hooks/use-course-resources-channel.test.tsx
```

The initial independent parser draft also passed native AMD64/ARM64 PostgreSQL/scanner/restart verification and the packaged-Java security scan. Its backend/frontend/product E2E jobs passed; the receipt title mismatch was corrected and the exact-head Engineering policy subsequently passed. These observations do not substitute for gates on the final integrated head.

## Review follow-up

A same-course polling response race was reproduced with deferred promises and fixed by invalidating older list responses at mutation start. Navigation also discards a late upload result. The backend integration gate exposed older authorization fixtures without the newly required READY field; those fixtures now declare READY and explicitly test stale vectors against all unready extraction outcomes. Full media+agent verification passes with the fail-closed catalog unchanged.

The real resource component was inspected in the dedicated browser at desktop width and an emulated 390-pixel viewport using labeled synthetic fixtures. Retry visibly moved FAILED to queued then READY; checking files retained disabled downloads; OCR/unsupported descriptions and the upload dialog remained readable without horizontal page overflow. These are component presentation/interaction observations, not a live-stack ingestion claim. Final durable-event system acceptance remains pending #245.

An extraction-signal fixture first failed because retrieved chunks did not expose normalization/instruction markers. Chunk reads now carry the atomically stored index signals to retrieval responses and the granted chunk tool. Real ingestion plus tool HTTP tests verify the warning survives normalization and remains marked as untrusted evidence; access checks are unchanged.

## Accepted event integration

Rebased onto accepted #245, preserving its search publisher and V3 migration. The new ResourceChanged contract carries source identity/version and authoritative Course scope through that existing outbox. The agent cursor commits queued work; a separate worker owns bounded extraction retry. Media synchronizes only the expected event/hash outcome. Direct legacy ingestion cannot overwrite an event-managed source. Instructor approval changes alter live eligibility immediately and publish a new event; event exhaustion is visible and manual retry has a new identity.

The initial queue test failed to compile before the durable contract existed. Integrated database tests now cover duplicate delivery, same-source reuse, supersession, wrong bytes, approval revocation, five-attempt exhaustion and terminal replay. Expiring the final lease also advances generation, rejecting its late completion. Latch-controlled durable ingestion tests verify deletion and newer source publication complete while old embedding is paused. Source HTTP tests check token, scope/checksum, availability, approval revoked during provider download and bounded response size. The complete local backend verify passes after these changes.

Native PostgreSQL tests now include persisted queued work and expired-claim recovery across process/database restart. A product drill uploads while the agent is stopped, waits for durable convergence, revokes approval while stopped, reapproves, then deletes while confirming quota remains reserved until the agent fence succeeds. These newly added hosted proofs and exact-head security/full review are still pending; the script's syntax check is not a system-test claim.

The integrated head subsequently passed native AMD64/ARM64 PostgreSQL/scanner/restart tests, complete hosted backend verification and packaged security. The real product ingestion drill passed stopped-worker delivery, authoritative scope, approval revocation/reapproval and terminal deletion/quota convergence. The later browser suite exposed the seed's chunks-before-READY grant race; its setup now waits for READY and verifies the intended grant candidate.

Completed review follow-up reproduced an older preview replacing the latest URL and permanent Course scope failures retrying as outages. Targeted tests failed before each fix and pass afterward; 28 resource UI tests and the production bundle build pass. JSON-null endpoint input already returned 400 and now has an explicit regression. Media status retries remain separate from the agent's bounded extraction work.

Rebased onto accepted #253. Release policy advances only to compatibility epoch 6 with the agent V9/V10 and media V4 rationale. A failing epoch-policy regression now passes and proves epoch-5 deployment/rollback executes no downgrade actions; all 18 deployment unit tests pass. Final gates must run again on this rebased head. Account-specific private-provider recovery remains open.
