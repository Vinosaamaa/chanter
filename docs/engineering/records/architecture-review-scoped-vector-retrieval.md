---
schemaVersion: 1
id: architecture-review-scoped-vector-retrieval
revision: 1
type: architecture-review
status: proposed
title: Scoped semantic retrieval and embedding version migration
repository: chanter
capabilityIds: ["resource-ingestion"]
createdAt: 2026-09-18
reconstructed: false
confidence: high
unknowns: ["Combined telemetry memory proof and exact-head integrated review gates remain pending"]
modules: ["agent-service", "media-service"]
interfaces: ["authorized-resource-vector-query", "embedding-version-migration"]
seams: ["current-resource-scope", "datastore-ranking", "generation-checked-publication"]
adapters: ["pgvector", "onnxruntime", "huggingface-tokenizers"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Scoped semantic retrieval", "Online embedding migration"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #247","url":"https://github.com/Vinosaamaa/chanter/issues/247","kind":"issue"},{"label":"pgvector","url":"https://github.com/pgvector/pgvector","kind":"documentation"}]
verification: {"state":"verified","evidenceRefs":["backend/agent-service/src/test/java/com/chanter/agent/infra/OnnxEmbeddingClientTest.java", "backend/agent-service/src/test/java/com/chanter/agent/infra/EmbeddingVersionStoreTest.java", "docs/operations/issue-247-change-log.md"]}
visibility: public-safe
publicationEligibility: eligible
issue: 247
pr: 330
release: null
run: null
---
# Scoped semantic retrieval and embedding version migration

Hashing does not provide production semantic relevance. Loading all embeddings into Java also makes retrieval memory proportional to corpus size. This change replaces that boundary with a live authorized resource set and a bounded pgvector query. Source version, Study Server, Course and Cohort checks occur in the query before results leave the datastore. Current media authorization and final evidence checks remain required.

The small real local model runs in process as the free default. The extended native image proof fits the existing 640 MiB allocation on AMD64 and ARM64. Pinned weights/tokenizer checksums and an explicit preprocessing revision define its coordinate space. There is no network download during inference and no hashing fallback. Operator-configured HTTP embeddings remain a separate explicit option, with no paid test calls.

Candidate versions are stored alongside the active version. Activation requires current chunk coverage; rollback requires retained complete coverage. Parsing/model calls remain outside resource locks, and final writes retain the #246 generation/chunk identity fence. Deletion removes every model's content. V13 follows #316's V11/V12; production pgvector installation requires the database owner before unprivileged agent migrations.

Native PostgreSQL and local tests establish asset integrity, real paraphrase separation, authorized SQL ranking, model-specific indexes, durable migration progress and concurrent deletion/rollback behavior. The actual image completed 100,000-chunk retrieval, full-window inference and four-client load below its memory allocation on both architectures, with captured query plans and valid indexes. The accepted #252 union preserves database bootstrap and telemetry and stamps epoch 8; its combined memory/export proof and exact-head integrated review remain required. The fixture reports 10/10 top-ranked sources and 8/10 confident answers without lowering the threshold. See the change log for evidence and limits; this record does not close #247.
