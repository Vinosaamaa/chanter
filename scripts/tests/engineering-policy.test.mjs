import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";

import { parseReceipt, parseRecord, validateHistoricalBatch, validatePullRequest } from "../engineering-policy.mjs";

const ROOT = resolve(import.meta.dirname, "../..");
const VALIDATOR = resolve(ROOT, "scripts/validate-engineering-impact.mjs");

function sha256(relativePath) {
  return createHash("sha256").update(readFileSync(resolve(ROOT, relativePath))).digest("hex");
}

test("Chanter vendors the exact released Engineering v1 schemas", () => {
  assert.equal(
    sha256("docs/contracts/engineering-pull-request-receipt.schema.json"),
    "3fb0c0d2f080291f0d3ded75d0581c89c564c93ba94acea3fcde05d402fbf9a0",
  );
  assert.equal(
    sha256("docs/contracts/engineering-journal-record.schema.json"),
    "31240beaa07f35074ee167e7521e64e88fcb6f229e407e50a23ed9befffb12d9",
  );
  assert.equal(
    sha256("docs/contracts/engineering-historical-backfill-batch.schema.json"),
    "88a0ed2739f32bcd3faa89a823b3acbfa106be9fb9faaf6b73ddff4bb5763237",
  );
});

function receipt({ pr = 281, title = "Adopt Engineering evidence contracts", classification = "none", refs = [] } = {}) {
  return `---
schemaVersion: 1
repository: chanter
pr: ${pr}
title: ${JSON.stringify(title)}
classification: ${classification}
richRecordRefs: ${JSON.stringify(refs)}
reconstructed: false
confidence: verified
unknowns: []
headCommit: null
mergeCommit: null
mergedAt: null
sources: [{"label":"Pull request #${pr}","url":"https://github.com/Vinosaamaa/chanter/pull/${pr}","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:${pr}"]}
visibility: public-safe
publicationEligibility: eligible
---
# ${title}

Added repository-owned Engineering evidence contracts without changing product runtime behavior.
`;
}

test("a non-material pull request validates its exact numbered receipt", () => {
  assert.equal(validatePullRequest({
    number: 281,
    title: "Adopt Engineering evidence contracts",
    body: "## Engineering impact\n\n- [x] None — reason: Policy documentation and local tooling only; product runtime is unchanged.",
    changedFiles: ["README.md", "docs/engineering/changes/pr-281.md"],
    receiptMarkdown: receipt(),
    recordsAtHead: [],
    changedRecordPaths: [],
    baseRecordPaths: [],
  }).classification, "none");
});

function record({ type = "architecture-review" } = {}) {
  return `---
schemaVersion: 1
id: architecture-review-chanter-engineering-evidence
revision: 1
type: ${type}
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

Chanter owns canonical Engineering receipts and rich records in Git.
`;
}

test("a material receipt resolves its exact matching rich-record revision", () => {
  const reference = "architecture-review-chanter-engineering-evidence@1";
  const result = validatePullRequest({
    number: 281,
    title: "Adopt Engineering evidence contracts",
    body: "## Engineering impact\n\n- [x] Architecture Review",
    changedFiles: [
      "docs/engineering/changes/pr-281.md",
      "docs/engineering/records/architecture-review-chanter-engineering-evidence.md",
    ],
    receiptMarkdown: receipt({ classification: "architecture-review", refs: [reference] }),
    recordsAtHead: [{
      path: "docs/engineering/records/architecture-review-chanter-engineering-evidence.md",
      markdown: record(),
    }],
    changedRecordPaths: ["docs/engineering/records/architecture-review-chanter-engineering-evidence.md"],
    baseRecordPaths: [],
  });
  assert.deepEqual(result.recordRefs, [reference]);
});

function git(root, ...arguments_) {
  return execFileSync("git", arguments_, { cwd: root, encoding: "utf8" }).trim();
}

test("the CLI validates exact base-to-head Git objects for a forward pull request", (t) => {
  const root = mkdtempSync(join(tmpdir(), "chanter-engineering-policy-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  git(root, "init", "--quiet");
  git(root, "config", "user.name", "Policy Fixture");
  git(root, "config", "user.email", "policy@example.com");
  writeFileSync(join(root, "README.md"), "# Fixture\n");
  git(root, "add", ".");
  git(root, "commit", "--quiet", "-m", "base");
  const base = git(root, "rev-parse", "HEAD");
  mkdirSync(join(root, "docs", "engineering", "changes"), { recursive: true });
  writeFileSync(join(root, "docs", "engineering", "changes", "pr-281.md"), receipt());
  git(root, "add", ".");
  git(root, "commit", "--quiet", "-m", "head");
  const head = git(root, "rev-parse", "HEAD");
  const eventPath = join(root, "event.json");
  writeFileSync(eventPath, JSON.stringify({
    pull_request: {
      number: 281,
      title: "Adopt Engineering evidence contracts",
      body: "## Engineering impact\n\n- [x] None — reason: Policy documentation and local tooling only; product runtime is unchanged.",
      base: { sha: base },
      head: { sha: head },
      html_url: "https://github.com/Vinosaamaa/chanter/pull/281",
    },
    repository: { name: "chanter" },
  }));

  const result = spawnSync(process.execPath, [VALIDATOR, eventPath], { cwd: root, encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Engineering impact: none; receipt PR #281; 1 changed file/);
});

test("historical publication is bounded, add-only, and owner-authorized", () => {
  const authorizationUrl = "https://github.com/Vinosaamaa/chanter/issues/280#issuecomment-123456";
  const historicalReceipt = receipt({
    pr: 7,
    title: "Preserve one historical Chanter change",
  }).replace("reconstructed: false", "reconstructed: true");
  const manifestPath = "docs/engineering/backfill/pr-281.json";
  const historicalPath = "docs/engineering/changes/pr-7.md";
  const input = {
    manifest: {
      schemaVersion: 1,
      repository: "chanter",
      pullRequest: 281,
      privacyAuthorizationUrl: authorizationUrl,
      receiptPaths: [historicalPath],
      recordRefs: [],
      addedRecordRefs: [],
    },
    manifestPath,
    pullRequestNumber: 281,
    repositoryFullName: "Vinosaamaa/chanter",
    changedFiles: [
      "docs/engineering/changes/pr-281.md",
      historicalPath,
      manifestPath,
    ],
    historicalReceipts: [{ path: historicalPath, markdown: historicalReceipt }],
    recordsAtHead: [],
    changedRecordPaths: [],
    baseExistingPaths: [],
    authorizationComment: {
      html_url: authorizationUrl,
      author_association: "OWNER",
      body: "I authorize publication of this bounded historical Engineering backfill batch under the residual-link policy.",
    },
  };
  assert.deepEqual(validateHistoricalBatch(input), { historicalReceiptCount: 1, historicalRecordCount: 0 });
  assert.throws(
    () => validateHistoricalBatch({ ...input, baseExistingPaths: [historicalPath] }),
    /add-only/,
  );
  assert.throws(
    () => validateHistoricalBatch({ ...input, authorizationComment: { ...input.authorizationComment, author_association: "CONTRIBUTOR" } }),
    /owner authorization/,
  );
});

test("bounded v1 documents reject private paths and invalid type/status pairs", () => {
  const privatePath = join("/", "Users", "person", "private", "notes.txt");
  assert.throws(
    () => parseReceipt(receipt({ title: privatePath })),
    /public-safe/,
  );
  assert.throws(
    () => parseRecord(
      "docs/engineering/records/architecture-review-chanter-engineering-evidence.md",
      record({ type: "feature-retrospective" }),
    ),
    /status.*type/,
  );
});

test("None reasons reject placeholder prefixes and punctuation-only text", () => {
  for (const reason of ["TODO pending details", "............."]) {
    assert.throws(() => validatePullRequest({
      number: 281,
      title: "Adopt Engineering evidence contracts",
      body: `## Engineering impact\n\n- [x] None — reason: ${reason}`,
      changedFiles: ["docs/engineering/changes/pr-281.md"],
      receiptMarkdown: receipt(),
      recordsAtHead: [],
      changedRecordPaths: [],
      baseRecordPaths: [],
    }), /concrete reason/);
  }
});

test("receipt and rich-record collections obey bounded verified metadata", () => {
  assert.throws(
    () => parseReceipt(receipt().replace(
      'sources: [{"label":"Pull request #281","url":"https://github.com/Vinosaamaa/chanter/pull/281","kind":"pull-request"}]',
      "sources: []",
    )),
    /sources/,
  );
  assert.throws(
    () => parseRecord(
      "docs/engineering/records/architecture-review-chanter-engineering-evidence.md",
      record().replace('modules: ["engineering-policy"]', `modules: ${JSON.stringify(Array.from({ length: 33 }, (_, index) => `module-${index}`))}`),
    ),
    /modules/,
  );
  assert.throws(
    () => parseRecord(
      "docs/engineering/records/architecture-review-chanter-engineering-evidence.md",
      record().replace('verification: {"state":"verified","evidenceRefs":["issue:280"]}', 'verification: {"state":"not-recorded","evidenceRefs":[]}'),
    ),
    /Verified.*evidence/,
  );
});
