# Resource extraction and ingestion correctness

Issue #246 owns parsing and truthful ingestion state. It builds on #244's current-resource authorization and permanent deletion markers. #245 owns durable publication and delivery; this work consumes that contract after integration and does not introduce another outbox.

## Extraction boundary

Use Apache PDFBox 3.0.8 for PDF and Apache POI 5.5.1 for DOCX/PPTX. These maintained format-specific libraries expose page, section and slide boundaries directly without enabling network parsers, OCR, macros or external processes. Text and Markdown require valid UTF-8. Sources: [PDFBox releases](https://pdfbox.apache.org/download.html), [POI releases](https://poi.apache.org/download.cgi).

Keep the 10 MiB source limit, cap decompressed Office data, entry count and extracted text, and reject oversized documents explicitly. Parser outcomes distinguish ready text, empty documents, scanned-only PDF requiring OCR, encryption, malformed input, unsupported formats and size limits. Audio/video is not advertised as AI-readable. A mixed PDF with image-only pages reports incomplete extraction rather than silently claiming every page is available.

Normalize extracted text consistently for chunking and citation verification. Store source locators separately from text; retain the original source checksum and parser version. Resource text remains untrusted quoted evidence. Record suspicious control characters/instruction markers as signals, not proof of malice and not executable instructions. Course-scoped resources remain course-scoped; do not invent a Cohort or language when the source does not supply one.

Extraction signals live on the resource index row and are committed with the chunks. Chunk reads include those signals from the same database statement, avoiding a second persisted copy. Retrieval responses and the granted chunk tool expose them; tool evidence is explicitly marked as untrusted. Signals neither grant access nor prove that text is safe.

## Persistence and deletion

Parsing and embedding preparation run outside the final database transaction. The final transaction locks the resource index row, checks terminal deletion and the expected source version, and replaces chunks plus embeddings atomically. An older completion cannot overwrite a newer source or restore a deleted ID. Direct backfill uses the same final validation. Existing live AVAILABLE/approved/course/viewer authorization remains independent of retained index rows.

Persist explicit ingestion state, source version/checksum and safe failure codes. Replayed matching content must not create duplicates. Transient provider failure is retryable; unsupported/encrypted/malformed sources require a changed source or explicit instructor action. Versioned event consumption follows accepted #245 and must not infer current permission from historical events.

## Durable delivery integration plan

Reuse #245's outbox envelope and consumer cursor. A dedicated media ResourceChanged event carries the resource/course/Study Server identity, source checksum, filename, approval and terminal-deletion flag. Course-wide sources have no Cohort; language is explicitly undetermined (`und`) and access scope is COURSE. Metadata comes from the owning Course record, never from a generated identifier or a learner's current Cohort.

The agent consumer commits only the accepted event and queued extraction state. A single agent worker claims the current event with a new generation and a durable lease, obtains the current approved source through an internal content endpoint, and prepares outside a transaction. Final publication must still match the claimed generation/event and the permanent deletion marker. Expired claims advance the generation before retry, so a late worker cannot publish. Bounded transient retries end in an instructor-visible FAILED state; unchanged READY content reuses existing chunks.

Media's existing INDEX task becomes outcome synchronization for its expected event/source checksum, not a second extraction retry owner. Instructor retry appends a new event in the media transaction. The existing synchronous agent deletion fence remains mandatory before media releases its storage reservation; the durable terminal event provides replay convergence. Historical events never authorize viewer retrieval, which continues to recheck current readiness, approval, course and permission.

## User and integration boundary

Instructor resource UI shows queued, processing, ready and actionable failure states, with retry only where meaningful. It must not imply OCR or transcription exists. UI work follows the vendored frontend-design skill and approved resource layouts. Existing citation fields remain compatible with native-companion signing; locator presentation is additive.

Real generated document fixtures cover each supported format, multiple locators, empty/scanned/encrypted/malformed documents, expansion/text limits and normalization. Concurrency tests cover delayed embedding versus deletion/replacement and atomic publication. Final acceptance requires package security, full review, native PostgreSQL evidence and browser evidence; parser unit success alone does not complete #246.

## Instructor status design

Keep the current learning-desk resource rows and Instrument Sans typography. Use paper white #ffffff and desk #f3f6fa, ink #192c46, muted #596a80, action blue #2458d3 and warning #8a5c12. Existing red/green status tokens retain their shared meanings. Resource title and file details stay left aligned; status and a compact retry action stay beside the same resource rather than opening a dashboard.

```
[file] Course guide                 [AI ready]          [open] [download]
       PDF  240 KB
[file] Scanned worksheet            [Text needed]       [download]
       PDF  180 KB
       Upload a version with selectable text.
[file] Lecture notes                [Preparation failed] [Retry AI preparation]
```

Show upload/scanning availability separately from AI preparation. Only AVAILABLE + approved + READY can say AI ready. Unknown or older state must never imply readiness. Retry is available to instructors for FAILED preparation only; scanned/encrypted/malformed/unsupported files explain the source change required. Downloads remain enabled for available files regardless of AI readability. Keep keyboard focus and names on each action, refresh pending state without moving focus, and wrap descriptions/actions on narrow screens. The visual emphasis belongs to the actionable state within each course resource row. Review against the existing layout rejected a separate metrics panel because it would hide the file-specific decision.
