---
schemaVersion: 1
id: feature-retrospective-chanter-ai-study-assistant-runtime
revision: 1
type: feature-retrospective
status: released
title: "feat(agent): #94 ingest and chunk AI-approved course resources"
repository: chanter
capabilityIds: ["chanter-ai-study-assistant-runtime"]
createdAt: 2026-07-15
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and workflow logs were not quoted.","A public launch was not evidenced; release truth remains strong local beta."]
modules: ["backend-agent-service","backend-media-service"]
interfaces: ["backend/agent-service/src/main/java/com/chanter/agent/api/InternalResourceIngestionController.java","backend/agent-service/src/main/resources/db/migration/V3__create_resource_chunk_tables.sql","backend/agent-service/src/test/java/com/chanter/agent/api/ResourceIngestionSmokeTest.java","backend/media-service/src/test/java/com/chanter/media/api/CourseResourceSmokeTest.java"]
seams: ["media-service-to-agent-service","approved-resource-to-persisted-chunks","service-authentication-to-internal-ingestion"]
adapters: ["backend/agent-service/src/main/java/com/chanter/agent/application/ResourceChunkRepository.java","backend/agent-service/src/main/java/com/chanter/agent/infra/JdbcResourceChunkRepository.java","backend/media-service/src/main/java/com/chanter/media/application/ResourceIngestionClient.java","backend/media-service/src/main/java/com/chanter/media/infra/HttpResourceIngestionClient.java"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Approved text resource ingestion","Stable text chunk offsets","Idempotent chunk replacement"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Pull request #164","url":"https://github.com/Vinosaamaa/chanter/pull/164","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:164","head-commit:9189a68738c84bdd22f0a4102715a9274bb81aed","merge-commit:36f2a46589fab62b143e4b4142c9e4b3140d1b74"]}
visibility: public-safe
publicationEligibility: eligible
issue: 94
pr: 164
release: null
run: null
---
# feat(agent): #94 ingest and chunk AI-approved course resources

## Context

The AI Study Assistant needed durable, reviewable text units derived only from Course Resources explicitly approved for AI use. Pull request #164 introduced the ingestion boundary without prematurely claiming semantic retrieval or broad file-format support.

## Delivered boundary

After an approved text resource upload, the Media Service called a service-authenticated Agent Service endpoint. The Agent Service extracted supported text, created chunks with stable character offsets, and persisted them in its own database. Re-ingesting the same resource replaced prior chunks so the stored representation tracked the current approved source.

The initial boundary accepted plain-text and Markdown resources. Embeddings, vector retrieval, PDF extraction, and replacement of the existing grounding path were explicitly deferred.

## Verification

Unit coverage exercised extraction and chunking, while service smokes covered ingestion and the Media-to-Agent hook. The PR recorded focused Maven tests and public-safe operational logging based on identifiers and content hashes rather than resource bodies.

## Consequences

Approved Course Resources gained a durable preprocessing path owned by the Agent Service. The design enabled later retrieval slices while keeping approval, source content, and derived chunks traceable and replaceable.

## Historical limits

No workflow or attachment bodies are reproduced, and the evidence does not establish a hosted AI service or public launch.
