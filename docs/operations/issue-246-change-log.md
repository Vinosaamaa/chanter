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
