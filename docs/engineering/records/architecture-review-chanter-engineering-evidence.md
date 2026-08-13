---
schemaVersion: 1
id: architecture-review-chanter-engineering-evidence
revision: 1
type: architecture-review
status: accepted
title: Chanter Engineering evidence boundary
repository: chanter
capabilityIds: ["engineering-evidence"]
createdAt: 2026-08-13
reconstructed: false
confidence: verified
unknowns: []
modules: ["engineering-policy","engineering-projection"]
interfaces: ["pull-request-evidence","historical-publication"]
seams: ["interview-arc-trusted-source-ingestion"]
adapters: ["github-actions","portable-static-export"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Complete pull request timeline","Curated rich Engineering evidence","Deterministic local projection"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Chanter issue #280","url":"https://github.com/Vinosaamaa/chanter/issues/280","kind":"issue"},{"label":"Interview Arc Engineering release PR #284","url":"https://github.com/Vinosaamaa/interview-arc/pull/284","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["issue:280","pull-request:284"]}
visibility: public-safe
publicationEligibility: eligible
issue: 280
pr: 281
release: null
run: null
---
# Chanter Engineering evidence boundary

## Context

Chanter needs the same compact-receipt and curated-rich-record semantics as the Interview Arc family, but it is a separate product with a separate repository, lifecycle, and deployment truth. Chanter is a strong local beta and has no Engineering website or production D1 ingestion boundary.

## Alternatives reviewed

- Store Engineering narrative in a new runtime database. Rejected because Git already owns reviewed, immutable narrative and Chanter has no runtime reader requirement.
- Copy generated HTML into source or publish it as a website. Rejected because HTML is a disposable projection and would become a competing source of truth.
- Let CI infer motivation and architecture from diffs. Rejected because diffs cannot establish intent, causality, privacy eligibility, or verified system structure.
- Reuse Arc’s repository as Chanter’s canonical owner. Rejected because it would mix independent project histories and weaken ownership.

## Decision

Chanter owns canonical receipts and rich records in Git, validates them against exact pull-request revisions, and generates deterministic repository-local JSON and portable static HTML. Every PR receives a compact receipt; only material changes or reviewed clusters receive rich prose. Historical publication is bounded, add-only, and separately owner-authorized.

## Integration boundary

The Chanter projection is not a public website and does not write D1. A later Arc PR may ingest an explicitly trusted Chanter commit. That pin, allowlist, website bundling, and deployment remain Arc-owned changes and are outside issue #280.

## Agent boundary

Repository instructions, the PR template, the non-interactive scaffold, and required CI form the self-teaching author flow. Any implementation agent owns the factual receipt and, when material, its rich record while implementation context is available. CI validates authored evidence but never writes narrative or diagrams.

## Verification

Contract hashes, scaffold behavior, exact base/head PR validation, historical authorization and batch limits, public-safety rules, immutable record handling, and byte-stable JSON/HTML projection are covered by repository tests and the `engineering-policy` CI job.

## Consequences

Future Chanter changes gain a complete factual PR timeline without requiring an article for every small fix. Rich evidence remains reviewable and correction-safe. Chanter can later be shown in the Interview Arc Engineering workspace without runtime GitHub fetches or a shared mutable database.
