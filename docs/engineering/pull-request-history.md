# Chanter Engineering evidence protocol

Chanter owns two canonical, public-safe evidence layers in Git:

| Layer | Coverage | Canonical path | Purpose |
| --- | --- | --- | --- |
| Pull Request Receipt | Every merged pull request | `docs/engineering/changes/pr-<number>.md` | Compact factual timeline, including small changes |
| Rich Engineering Record | Material change or reviewed PR cluster | `docs/engineering/records/*.md` | Decisions, architecture, incidents, retrospectives, and capability context |

The versioned contracts are `docs/contracts/engineering-pull-request-receipt.schema.json`, `docs/contracts/engineering-journal-record.schema.json`, and `docs/contracts/engineering-historical-backfill-batch.schema.json`. Chanter vendors those released v1 contracts byte-for-byte so evidence keeps the same semantics as the Interview Arc family while remaining a separate project family.

## Self-teaching author flow

“Self-teaching” means the repository itself teaches and enforces authorship. It does not mean CI invents prose. Codex, Claude, Grok, a cloud coordinator, or a human contributor follows the same path:

1. During issue implementation, decide whether the change is material.
2. If material, author or reuse an evidence-backed rich record at an exact `id@revision`.
3. Open a draft pull request to obtain its Chanter PR number.
4. Run `node scripts/new-engineering-receipt.mjs --help`, then scaffold `docs/engineering/changes/pr-<number>.md`.
5. Select exactly one matching `Engineering impact` checkbox in the PR body.
6. Commit the receipt and any new rich record before review.
7. CI validates the PR metadata and exact base/head Git objects, then proves the derived projection is deterministic.

The PR template points to the command, the command explains material and non-material examples, the agent workflow makes authorship mandatory, and CI rejects omissions. An external agent does not need a separate prompt if it reads repository instructions; an agent that ignores them receives a concrete merge-blocking CI failure.

Every PR gets a receipt. Small work selects `none`, gives a concrete reason, and does not receive inflated long-form prose. Material work uses one of: `change-note`, `adr`, `architecture-review`, `feature-retrospective`, `postmortem`, or `capability-dossier`. One rich record may cover a coherent cluster of PRs, and each receipt links the same exact revision.

## Canonical and generated artifacts

Canonical Markdown is the source of truth. `node scripts/build-engineering-journal.mjs` derives:

- `.engineering-journal/generated/index.json` with receipts, records, search entries, backlinks, and Statistics;
- `.engineering-journal/generated/portable.html`, a self-contained static export that can be opened or archived without React, a database, or a server.

Both generated files are disposable and gitignored. The portable HTML is not Chanter’s public website and is never copied into narrative source. Chanter currently remains a strong local beta; this adoption does not claim a public deployment.

Interview Arc’s website is a separate consumer. A later reviewed Arc change may add an immutable Chanter commit pin and ingest that released commit during Arc’s build. Until that happens, Chanter evidence remains repository-local. Neither Chanter nor Arc inserts Engineering narrative into D1, and neither product fetches mutable GitHub content at runtime.

## Materiality and diagrams

Use a rich record when a change materially affects a Module or Interface, durable state or ownership, a schema or migration, a cross-repository protocol, security, privacy, reliability, accessibility, performance, incident repair, or a difficult-to-reverse tradeoff.

Receipts never generate diagrams. A rich record may reference a public-safe `.drawio` source plus a PNG or SVG only when verified structure or flow is materially clearer visually. Both assets must be committed and evidence-backed. CI and the projection never invent architecture from a diff.

## Corrections and historical publication

Accepted rich records are immutable. Correct them with a reviewed amendment or superseding revision so exact historical links continue to resolve.

Historical work uses reconstructed receipts and the same rich-record contract. Each PR is bounded by `docs/engineering/backfill/pr-<current-pr>.json`: at most 20 reconstructed receipts and at most 8 newly added rich records. The batch is add-only, contains no unrelated files, resolves every rich link, and requires an exact GitHub owner comment in Chanter:

> I authorize publication of this bounded historical Engineering backfill batch under the residual-link policy.

The authorization applies only to the identified batch. It does not authorize history rewriting, deletion, repository visibility changes, or later batches.
