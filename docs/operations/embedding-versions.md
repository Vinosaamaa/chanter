# Semantic embeddings and model rotation

The agent uses the pinned `sentence-transformers/all-MiniLM-L6-v2` ONNX model by default. The release build downloads and checks the model and tokenizer; the agent verifies both again at startup. Runtime inference never downloads a model. The packaged directory is `/app/models/minilm`, selected with `CHANTER_EMBEDDINGS_MODEL_DIRECTORY`. Hashing is a test-profile fixture and production rejects it.

The model runs one inference at a time on one native thread, truncates at 256 tokens and accepts at most 16,384 input characters. Requests wait at most two seconds for inference capacity. Unavailable embeddings, missing vectors or insufficient similarity produce no resource evidence and recommend human support. Approved FAQs remain a separate source. There is no downloaded-resource keyword fallback.

V13 requires pgvector in the agent database before Flyway runs. The cluster owner creates the extension; the application role does not receive superuser privileges. V13 discards old hashing coordinates and preserves current chunks for rebuilding. Epoch 8 prevents downgrade to a binary that expects the former BYTEA schema. Current resource scope, source checksum and live viewer approval remain required regardless of the vector version.

## Optional embeddings API

Operators may configure up to two OpenAI-compatible embeddings endpoints through `chanter.embeddings.api-models`. Each entry requires `id`, `base-url`, `model`, `revision`, `dimensions`, and an optional `api-key` supplied through the deployment secret configuration. The immutable ID identifies provider, model revision, dimensions and normalization; never reuse it for changed model behavior. Use an upstream versioned model name and a new local ID when its revision changes. Responses must identify the configured model, contain one indexed result with the exact dimensions, and contain finite nonzero coordinates. HTTPS is required except for an explicit loopback endpoint. Response size and time are bounded; no other provider is tried on failure.

`CHANTER_EMBEDDINGS_PROVIDER=api` disables the local model. `CHANTER_EMBEDDINGS_DEFAULT_MODEL` must name a configured API entry for an empty database. For an existing database, the persisted active version wins. All active, candidate and rollback models must remain configured at startup. Configuring an API endpoint does not grant resource access; queries still use current media authorization. A paid endpoint runs only when explicitly configured and selected by the operator. Repository verification uses loopback fixtures and the free local model.

## Online rotation

Private endpoints under `/api/v1/internal/embedding-versions` require the internal service token and are not public gateway APIs. Provider URLs, credentials and model files cannot be supplied through these endpoints.

1. Configure the new immutable model alongside the current one and restart the agent. Read `GET /embedding-versions` to inspect active/candidate/previous IDs, missing chunks, indexed chunks and failed resources.
2. `POST /stage` with `{"modelId":"new-version-id"}` schedules durable rebuilding. Current queries and ingestion keep using the active model. Each resource/model job has a generation-bound lease. Inference runs outside database transactions; final publication checks the source generation, chunk identities, current model and lease.
3. Failed work retries after 30 seconds, up to five attempts. A crashed worker's lease expires after 30 minutes. After five attempts, inspect the provider/configuration and use `POST /retry` with `resourceId` and `modelId`. Progress survives process restarts. Very slow providers may require smaller source documents or faster inference to complete within one lease; retries never publish expired work.
4. When `missingChunks` is zero, `POST /activate` with the candidate ID. Activation checks current coverage under the model control lock. It retains the old serving model for rollback. Discard any older rollback version before a subsequent activation.
5. `POST /rollback` selects the retained version only when it covers all current chunks. New or replaced resources may require background rebuilding into that retained version first. Discard a staged candidate before rollback.
6. `POST /discard` with an unused candidate or previous ID deletes its retained vectors and jobs. The serving version cannot be discarded. The immutable catalog identity and empty index remain so the same configured version may later be rebuilt. At most the active, candidate and previous versions retain coordinates.

Terminal resource deletion removes every model's chunks, vectors and rebuild jobs while retaining the deletion marker. A delayed provider may finish its computation, but its expired/deleted generation cannot publish. Purge/reset retains the ability to ingest the same live resource ID again.

## Runtime evidence

The native workflow exercises the packaged agent at its 640 MiB allocation and shares two CPUs with the 896 MiB PostgreSQL fixture. It checks real paraphrase relevance separately from a generated 100,000-chunk load corpus, reports p50/p95 inference/database latency and peak container memory, and preserves the actual scoped query plan. The load corpus repeats ten semantic vectors across 1,000 resources and ten courses; it establishes bounded query behavior, not broad educational-answer quality. Final measured results and limitations belong in the issue review record after both architectures finish.

Native ONNX/tokenizer libraries are extracted from the pinned Maven artifacts during image construction and loaded from the immutable image filesystem. This preserves the non-executable runtime scratch mount; no executable temporary directory is required. The first packaged proof exposed the default JNI extraction into non-executable scratch, so runtime evidence must include this corrected image.
