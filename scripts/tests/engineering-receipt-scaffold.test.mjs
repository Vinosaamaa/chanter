import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const SCRIPT = resolve(fileURLToPath(new URL("../new-engineering-receipt.mjs", import.meta.url)));

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "chanter-engineering-receipt-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  mkdirSync(join(root, "docs", "contracts"), { recursive: true });
  mkdirSync(join(root, "docs", "engineering", "changes"), { recursive: true });
  writeFileSync(join(root, "CONTEXT.md"), "# Fixture\n");
  writeFileSync(join(root, "docs", "contracts", "engineering-pull-request-receipt.schema.json"), "{}\n");
  return root;
}

function scaffold(root, arguments_) {
  return spawnSync(process.execPath, [SCRIPT, ...arguments_], { cwd: root, encoding: "utf8" });
}

test("a coordinator scaffolds one canonical non-material Chanter receipt", (t) => {
  const root = fixture(t);
  const result = scaffold(root, [
    "--pr", "281",
    "--title", "Adopt Engineering evidence contracts",
    "--summary", "Added repository-owned Engineering evidence contracts without changing product runtime behavior.",
    "--classification", "none",
  ]);

  assert.equal(result.status, 0, result.stderr);
  const markdown = readFileSync(join(root, "docs", "engineering", "changes", "pr-281.md"), "utf8");
  assert.match(markdown, /^repository: chanter$/m);
  assert.match(markdown, /^pr: 281$/m);
  assert.match(markdown, /^classification: none$/m);
  assert.match(markdown, /^reconstructed: false$/m);
  assert.match(markdown, /https:\/\/github\.com\/Vinosaamaa\/chanter\/pull\/281/);
});

test("the scaffold rejects public-unsafe prose without echoing it", (t) => {
  const root = fixture(t);
  const unsafe = join("/", "Users", "person", "Projects", "private", "notes.txt");
  const result = scaffold(root, [
    "--pr", "282",
    "--title", "Document one safe Engineering change",
    "--summary", unsafe,
    "--classification", "none",
  ]);

  assert.equal(result.status, 1);
  assert.doesNotMatch(result.stderr, new RegExp(unsafe.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
  assert.throws(() => readFileSync(join(root, "docs", "engineering", "changes", "pr-282.md")), /ENOENT/);
});

test("material scaffolding requires sorted unique exact rich-record references", (t) => {
  const root = fixture(t);
  const result = scaffold(root, [
    "--pr", "283",
    "--title", "Adopt the Engineering evidence boundary",
    "--summary", "Adopted the reviewed Engineering evidence boundary for Chanter pull requests.",
    "--classification", "architecture-review",
    "--rich-record-ref", "second-record@2",
    "--rich-record-ref", "first-record@1",
  ]);
  assert.equal(result.status, 0, result.stderr);
  assert.match(
    readFileSync(join(root, "docs", "engineering", "changes", "pr-283.md"), "utf8"),
    /^richRecordRefs: \["first-record@1","second-record@2"\]$/m,
  );

  for (const [pr, suffix] of [
    [284, []],
    [285, ["--rich-record-ref", "Bad@0"]],
    [286, ["--rich-record-ref", "same@1", "--rich-record-ref", "same@1"]],
  ]) {
    const rejected = scaffold(root, [
      "--pr", String(pr),
      "--title", "Adopt the Engineering evidence boundary",
      "--summary", "Adopted the reviewed Engineering evidence boundary for Chanter pull requests.",
      "--classification", "architecture-review",
      ...suffix,
    ]);
    assert.equal(rejected.status, 1);
  }
});

test("help teaches the draft-PR authorship boundary without repository state", () => {
  const result = spawnSync(process.execPath, [SCRIPT, "--help"], { cwd: tmpdir(), encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Decide the Engineering impact before opening the pull request/);
  assert.match(result.stdout, /Open a draft pull request to obtain its repository-local number/);
  assert.match(result.stdout, /commit a new rich record/);
  assert.match(result.stdout, /existing exact revision already available at the pull-request head/);
  assert.match(result.stdout, /does not author prose or diagrams/);
});
