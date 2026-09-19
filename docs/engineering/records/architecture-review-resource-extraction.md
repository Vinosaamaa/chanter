---
schemaVersion: 1
id: architecture-review-resource-extraction
revision: 1
type: architecture-review
status: proposed
title: Resource extraction, source locations and generation-fenced ingestion
repository: chanter
capabilityIds: ["resource-ingestion"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Final integrated-head system and review gates remain pending"]
modules: ["agent-service", "media-service", "community-service", "common", "frontend"]
interfaces: ["resource-chunk-ingestion", "current-evidence-authorization"]
seams: ["bounded-document-extraction", "generation-checked-index-publication", "terminal-deletion", "source-locator-citations"]
adapters: ["apache-pdfbox", "apache-poi", "postgresql"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Document text extraction", "Source location metadata", "Concurrent ingestion and deletion"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #246","url":"https://github.com/Vinosaamaa/chanter/issues/246","kind":"issue"},{"label":"Apache PDFBox releases","url":"https://pdfbox.apache.org/download.html","kind":"documentation"},{"label":"Apache POI releases","url":"https://poi.apache.org/download.cgi","kind":"documentation"}]
verification: {"state":"verified","evidenceRefs":["backend/agent-service/src/test/java/com/chanter/agent/application/DocumentExtractionTest.java", "backend/agent-service/src/test/java/com/chanter/agent/infra/ResourceIndexDeletionFenceTest.java", "docs/operations/issue-246-change-log.md"]}
visibility: public-safe
publicationEligibility: eligible
issue: 246
pr: 325
release: null
run: null
---
# Resource extraction, source locations and generation-fenced ingestion

## Decision and boundary

A resource can be malware-clean while still unreadable by AI. The agent now parses UTF-8 text/Markdown, PDF through Apache PDFBox 3.0.8, and DOCX/PPTX through Apache POI 5.5.1. These format-specific adapters preserve page, slide and body-section locations without enabling OCR, macro execution, external processes or network fetching. Source SHA-256 and parser version accompany the normalized-text checksum. A document's text stays untrusted evidence.

The extractor caps input at 10 MiB, extracted text at two million characters, document units at 400, ZIP entries at 2,048, individual expanded entries at 16 MiB and total expanded Office content at 64 MiB. Encryption, malformed input, unsupported formats, limits, empty documents and OCR-required input have distinct durable outcomes. Any nontext graphical PDF page or PowerPoint slide fails the whole extraction as OCR-required; the parser does not publish a misleading partial set of pages. Image-only Word content also requires OCR. Word headers/footers and slide speaker notes are outside the initial body-text contract and produce explicit limitation signals. Images mixed with text are not transcribed. Audio/video is unsupported.

Unicode normalization and directional-control removal are shared by indexing and current-source citation validation. Instruction-like markers are diagnostic signals; they neither grant authority nor trigger document instructions. Existing live AVAILABLE, approval, course and viewer checks remain mandatory. SourceCitation keeps its existing three fields, with a page/slide/section suffix in its title; native signing can retain its existing evidence contract.

## Transaction and failure review

The previous ingestion/backfill transaction held a resource lock while computing embeddings. A paused provider consequently delayed deletion. Preparation now sits between two short transactions. Starting replacement locks the live row, advances a durable generation, clears previous chunks/embeddings, and records PROCESSING. Parsing and embedding use no resource transaction. Completion locks again and requires the same generation plus a nonterminal resource before atomically publishing chunks, embeddings and outcome. A newer request or deletion wins permanently over an older completion. Backfill validates both generation and the exact chunk IDs before replacing embeddings.

Matching READY source bytes, filename and parser version reuse the same chunks without recomputation. Failures do not publish partial vectors, provider diagnostics or source text. Accepted #245's outbox now delivers media source metadata into a short agent consumer transaction. The persisted job owns extraction retries, advances generation on claim and recovers expired leases. Five failed attempts produce an explicit FAILED result; instructor retry creates a new event. Migration purge increments the generation and clears content without retiring the ID. Terminal deletion clears all source/course/filename/job metadata and retains only the ID, terminal state and generation required to reject late work.

Media synchronizes only the result matching its expected event and source checksum. Approval changes immediately revoke live eligibility and emit an event; deletion keeps synchronous terminal-fence confirmation before quota release. Source downloads check AVAILABLE, approval, course and SHA before and after the provider read. Study Server metadata comes from the owning Course, including legacy enrichment; it never uses a fabricated viewer. Chunk metadata retains course-wide Cohort=null, language=und and accessScope=COURSE. These historical fields do not authorize retrieval.

The final transaction can still make deletion wait briefly for database writes. It never waits for document parsing or embedding calls. The current optional local Ollama adapter still has its existing per-call timeout; whole-ingestion scheduling and cancellation are integration concerns, not a claim that provider execution itself can be revoked after content was sent.

## Alternatives and compatibility

An omnibus parser with automatic OCR would add executables, formats and network behavior outside this scope. Format-specific maintained libraries make resource limits and source locations reviewable. Keeping all preparation under a row lock is simple but gives provider latency control over deletion. Publishing chunks before vectors allows partially ready content; a single final transaction avoids it.

V9 introduces generation and extraction metadata; V10 adds consumer cursors and durable ingestion jobs. Media V4 follows accepted #245 V3 and adds outcome/source-event metadata. Older writers do not honor the generation boundary, so this change requires a deployment epoch after accepted issue #245 (planned epoch 6). Downgrade to pre-246 application code against this schema is unsupported. No production configuration is changed here.

## Evidence and remaining acceptance

Generated real documents exercise multisection Word tables, multipage PDF, multislide PowerPoint, encrypted PDF/Office, image-only and mixed sources, expansion limits, strict UTF-8, normalization and malformed bytes. Database tests preserve locators/checksums and prove failed preparation retains no chunks/vectors. Latch-controlled embedding tests prove deletion completes while the provider is paused, late writes fail, newer ingestion remains current, and same-source replay avoids duplicate embedding. The same deletion suite is inherited by native PostgreSQL tests.

The media worker publishes durable source events, persists explicit extraction outcomes and exposes instructor retry. Current AI catalog authorization additionally requires READY. The resource component shows queued/processing/ready and source-specific failure states, keeps downloads separate from AI readiness, and explains that OCR/transcription are unavailable. Browser inspection of labeled synthetic fixtures covered desktop and 390-pixel layouts, retry transitions, unavailable downloads and the upload dialog; it does not prove live-stack ingestion.

The independent draft passed full hosted CI, native AMD64/ARM64 PostgreSQL/scanner/restart tests and packaged-Java security. Completed full review findings drove production format wiring and stale/transient UI response fixes. Accepted #245 resolves migration ordering. The integrated durable implementation passed the complete local backend verification, including bounded retries, stale claims, approval revocation, source authorization and concurrent generation tests. Final exact-head native restart, real product outage flow, package security and completed full review remain acceptance requirements. This record does not close issue #246.
