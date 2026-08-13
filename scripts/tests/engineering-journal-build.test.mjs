import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { buildEngineeringJournal } from "../build-engineering-journal.mjs";

function git(root, ...arguments_) {
  return execFileSync("git", arguments_, { cwd: root, encoding: "utf8", env: {
    ...process.env,
    GIT_AUTHOR_DATE: "2026-08-13T20:00:00Z",
    GIT_COMMITTER_DATE: "2026-08-13T20:00:00Z",
  } }).trim();
}

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "chanter-engineering-build-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, "docs", "engineering", "changes"), { recursive: true });
  mkdirSync(join(root, "docs", "engineering", "records"), { recursive: true });
  writeFileSync(join(root, "docs", "engineering", "changes", "pr-7.md"), `---
schemaVersion: 1
repository: chanter
pr: 7
title: "Preserve deterministic evidence"
classification: architecture-review
richRecordRefs: ["architecture-review-evidence-boundary@1"]
reconstructed: false
confidence: verified
unknowns: []
headCommit: null
mergeCommit: null
mergedAt: null
sources: [{"label":"Pull request #7","url":"https://github.com/Vinosaamaa/chanter/pull/7","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:7"]}
visibility: public-safe
publicationEligibility: eligible
---
# Preserve deterministic evidence

Preserved one deterministic Engineering evidence boundary.
`);
  writeFileSync(join(root, "docs", "engineering", "records", "architecture-review-evidence-boundary.md"), `---
schemaVersion: 1
id: architecture-review-evidence-boundary
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
modules: ["engineering-policy"]
interfaces: ["pull-request-evidence"]
seams: []
adapters: ["github-actions"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Engineering evidence"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Issue #280","url":"https://github.com/Vinosaamaa/chanter/issues/280","kind":"issue"}]
verification: {"state":"verified","evidenceRefs":["issue:280"]}
visibility: public-safe
publicationEligibility: eligible
issue: 280
pr: null
release: null
run: null
---
# Chanter Engineering evidence boundary

## Decision

Chanter owns its canonical Engineering evidence in Git.
`);
  git(root, "init", "--quiet");
  git(root, "config", "user.name", "Projection Fixture");
  git(root, "config", "user.email", "projection@example.com");
  git(root, "add", ".");
  git(root, "commit", "--quiet", "-m", "fixture evidence");
  return root;
}

test("the local Chanter projection is deterministic JSON plus portable static HTML", (t) => {
  const root = fixture(t);
  const first = buildEngineeringJournal({ root });
  const second = buildEngineeringJournal({ root });
  assert.equal(first.indexJson, second.indexJson);
  assert.equal(first.portableHtml, second.portableHtml);
  assert.equal(first.index.project, "chanter");
  assert.equal(first.index.productStatus, "strong-local-beta");
  assert.equal(first.index.pullRequestReceipts.length, 1);
  assert.equal(first.index.records.length, 1);
  assert.deepEqual(first.index.receiptBacklinks["architecture-review-evidence-boundary@1"], [7]);
  assert.equal(first.index.statistics.pullRequestCount, 1);
  assert.match(first.portableHtml, /Portable Chanter Engineering evidence/);
  assert.match(first.portableHtml, /architecture-review-evidence-boundary@1/);
  assert.doesNotMatch(first.portableHtml, /<script/u);
});

test("projection rejects a material receipt linked to the wrong rich-record type", (t) => {
  const root = fixture(t);
  const receiptPath = join(root, "docs", "engineering", "changes", "pr-7.md");
  const current = execFileSync("git", ["show", "HEAD:docs/engineering/changes/pr-7.md"], { cwd: root, encoding: "utf8" });
  writeFileSync(receiptPath, current.replace("classification: architecture-review", "classification: change-note"));
  git(root, "add", ".");
  git(root, "commit", "--quiet", "-m", "mismatch evidence");
  assert.throws(() => buildEngineeringJournal({ root }), /classification.*type/);
});
