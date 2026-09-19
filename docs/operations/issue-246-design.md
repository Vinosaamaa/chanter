# Resource extraction and ingestion correctness

Issue #246 owns parsing and truthful ingestion state. It builds on #244's current-resource authorization and permanent deletion markers. #245 owns durable publication and delivery; this work consumes that contract after integration and does not introduce another outbox.

## Extraction boundary

Use Apache PDFBox 3.0.8 for PDF and Apache POI 5.5.1 for DOCX/PPTX. These maintained format-specific libraries expose page, section and slide boundaries directly without enabling network parsers, OCR, macros or external processes. Text and Markdown require valid UTF-8. Sources: [PDFBox releases](https://pdfbox.apache.org/download.html), [POI releases](https://poi.apache.org/download.cgi).

Keep the 10 MiB source limit, cap decompressed Office data, entry count and extracted text, and reject oversized documents explicitly. Parser outcomes distinguish ready text, empty documents, scanned-only PDF requiring OCR, encryption, malformed input, unsupported formats and size limits. Audio/video is not advertised as AI-readable. A mixed PDF with image-only pages reports incomplete extraction rather than silently claiming every page is available.

Normalize extracted text consistently for chunking and citation verification. Store source locators separately from text; retain the original source checksum and parser version. Resource text remains untrusted quoted evidence. Record suspicious control characters/instruction markers as signals, not proof of malice and not executable instructions. Course-scoped resources remain course-scoped; do not invent a Cohort or language when the source does not supply one.

## Persistence and deletion

Parsing and embedding preparation run outside the final database transaction. The final transaction locks the resource index row, checks terminal deletion and the expected source version, and replaces chunks plus embeddings atomically. An older completion cannot overwrite a newer source or restore a deleted ID. Direct backfill uses the same final validation. Existing live AVAILABLE/approved/course/viewer authorization remains independent of retained index rows.

Persist explicit ingestion state, source version/checksum and safe failure codes. Replayed matching content must not create duplicates. Transient provider failure is retryable; unsupported/encrypted/malformed sources require a changed source or explicit instructor action. Versioned event consumption follows accepted #245 and must not infer current permission from historical events.

## User and integration boundary

Instructor resource UI shows queued, processing, ready and actionable failure states, with retry only where meaningful. It must not imply OCR or transcription exists. UI work follows the vendored frontend-design skill and approved resource layouts. Existing citation fields remain compatible with native-companion signing; locator presentation is additive.

Real generated document fixtures cover each supported format, multiple locators, empty/scanned/encrypted/malformed documents, expansion/text limits and normalization. Concurrency tests cover delayed embedding versus deletion/replacement and atomic publication. Final acceptance requires package security, full review, native PostgreSQL evidence and browser evidence; parser unit success alone does not complete #246.
