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
