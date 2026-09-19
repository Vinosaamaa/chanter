# Scoped production vector retrieval

Issue #247 replaces hashing and application-memory scans. The accepted #246 generation fence and current media authorization remain mandatory. This branch starts from accepted #246 and reserves agent migrations V13 onward, after #316 V11/V12. Production PostgreSQL changes are coordinated with the #252 owner.

## Retrieval boundary

The viewer's current media catalog and the installed assistant's grants determine resource identities and source hashes before any vector query. The database query must join that explicit authorization set, current READY lifecycle, matching Study Server/Course/Cohort scope, source version, and one active embedding version before returning ranked chunks. Course-wide resources retain Cohort=null; unknown scope does not grant access. A live authorization check still precedes evidence release and answer acceptance. No global top-k query followed by application permission filtering is allowed.

PostgreSQL stores supported pgvector values. Similarity ranking and limiting execute in SQL, with bounded results and a model-specific index. Tests must distinguish selective authorization from global nearest-neighbor truncation. Historical BYTEA embeddings are not trusted as semantic vectors and are rebuilt from current chunks.

## Embedding provider and migration

The free-default candidate is the real sentence-transformers all-MiniLM-L6-v2 model, pinned by revision and file checksum, with bounded CPU inference through ONNX Runtime. Feasibility is pending measured native AMD64/ARM64 memory, relevance and latency under the existing runtime budget. A provider-agnostic embeddings HTTP adapter is an explicit configuration option. No paid calls or automatic provider fallback are permitted. Hashing is available only to isolated tests.

Each model version records provider, immutable model revision, dimensions and normalization contract. Stored vectors identify that version. Re-embedding writes a separate candidate version while the active version continues serving. Progress is durable, retries are bounded, and final publication uses the existing generation/chunk identity fence. Activation requires complete current coverage; rollback selects a retained compatible version. A resource replacement or deletion invalidates every model's old vectors. Queries never compare mixed dimensions or substitute a different model on failure.

## Required evidence

Unit and real PostgreSQL tests cover empty/low-score results, wrong model/dimension, stale content, revocation, cross-Course and cross-Cohort isolation, selective authorized nearest neighbors, online migration, rollback and concurrent replacement/deletion. Real-model relevance fixtures must succeed on paraphrases and reject unrelated material. A realistic generated corpus measures p50/p95 database retrieval, bounded response size, process memory and query plans. The explicit #247 latency/memory acceptance will be based on measured evidence, not a hashing fixture. Full CI, native validation, packaged security and completed full CodeAnt review apply to the final union after #316.

Sources: [pgvector filtering and indexing](https://github.com/pgvector/pgvector), [model card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2), [ONNX Runtime Java](https://onnxruntime.ai/docs/get-started/with-java.html). Published artifact contents, not an outdated platform table alone, determine native architecture support.
