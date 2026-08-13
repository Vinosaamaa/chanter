#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";

const OWNER = "Vinosaamaa";
const REPOSITORY = "chanter";
const CLASSIFICATIONS = new Set([
  "none", "change-note", "adr", "architecture-review",
  "feature-retrospective", "postmortem", "capability-dossier",
]);
const RECORD_REF = /^[a-z0-9]+(?:-[a-z0-9]+)*@[1-9]\d*$/u;
const PUBLIC_UNSAFE_PATTERNS = [
  /(?:^|[\s("'`])\/(?:Users|home|root)\/[^\s)"'`]+/m,
  /(?:^|[\s("'`])\/(?:private\/tmp|tmp|var|opt|srv|workspace|mnt|Volumes)\/[^\s)"'`]+/m,
  /(?:^|[\s("'`])~\/[^\s)"'`]+/m,
  /\b[A-Za-z]:\\[^\s"'`]+/,
  /\\\\[^\s\\]+\\[^\s"'`]+/,
  /\bgh[pousr]_[A-Za-z0-9]{20,}\b/,
  /\bsk-[A-Za-z0-9_-]{20,}\b/,
  /\bAKIA[0-9A-Z]{16}\b/,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  /\b(?:password|access[_-]?token|api[_-]?key|client[_-]?secret)\s*[:=]\s*[^\s]{8,}/i,
  /\b(?:thread|task)_[A-Za-z0-9_-]{8,}\b/,
  /\bgit@[A-Za-z0-9.-]+:[^\s]+/,
  /https?:\/\/[^\s/@:]+:[^\s/@]+@[^\s/]+/,
  /\b[A-Z0-9._%+-]+@(?!example\.com\b)[A-Z0-9.-]+\.[A-Z]{2,}\b/i,
];
const HELP = `Create the canonical compact Engineering receipt for a Chanter pull request.

Authoring order:
  1. Decide the Engineering impact before opening the pull request.
  2. For material work, commit a new rich record first, or select an
     existing exact revision already available at the pull-request head.
  3. Open a draft pull request to obtain its repository-local number.
  4. Run this command and commit the generated pr-<number>.md file.
  5. Select the matching Engineering-impact checkbox. None requires a concrete reason.

Non-material example:
  node scripts/new-engineering-receipt.mjs \\
    --pr <number> \\
    --title "Correct one Engineering label" \\
    --summary "Corrected one local label without changing a Module or Interface." \\
    --classification none

Material example:
  node scripts/new-engineering-receipt.mjs \\
    --pr <number> \\
    --title "Adopt the Engineering evidence boundary" \\
    --summary "Adopted the reviewed evidence boundary for Chanter changes." \\
    --classification architecture-review \\
    --rich-record-ref <id>@<revision>

This scaffold creates canonical Markdown only. CI validates it, and a deterministic
build derives JSON and portable static HTML. It does not author prose or diagrams.
`;

function fail(message) {
  throw new Error(message);
}

function argumentsFrom(argv) {
  const values = new Map();
  const richRecordRefs = [];
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    const value = argv[index + 1];
    if (!["--pr", "--title", "--summary", "--classification", "--rich-record-ref"].includes(flag) || value === undefined) {
      fail("Use only the documented explicit options.");
    }
    if (flag === "--rich-record-ref") richRecordRefs.push(value);
    else if (values.has(flag)) fail(`Provide ${flag} exactly once.`);
    else values.set(flag, value);
  }
  for (const flag of ["--pr", "--title", "--summary", "--classification"]) {
    if (!values.has(flag)) fail(`Missing ${flag}.`);
  }
  return { values, richRecordRefs };
}

function oneLine(value, label, maximum) {
  if (!value || value !== value.trim() || /[\p{Cc}\p{Cs}\u2028\u2029]/u.test(value)) {
    fail(`${label} must be one non-empty line without surrounding whitespace.`);
  }
  if ([...value].length > maximum) fail(`${label} exceeds ${maximum} characters.`);
  return value;
}

function requirePublicSafe(...values) {
  if (values.some((value) => PUBLIC_UNSAFE_PATTERNS.some((pattern) => pattern.test(value)))) {
    fail("Receipt text is not public-safe.");
  }
}

function render({ pr, title, summary, classification, richRecordRefs }) {
  const url = `https://github.com/${OWNER}/${REPOSITORY}/pull/${pr}`;
  return `---
schemaVersion: 1
repository: ${REPOSITORY}
pr: ${pr}
title: ${JSON.stringify(title)}
classification: ${classification}
richRecordRefs: ${JSON.stringify(richRecordRefs)}
reconstructed: false
confidence: verified
unknowns: []
headCommit: null
mergeCommit: null
mergedAt: null
sources: ${JSON.stringify([{ label: `Pull request #${pr}`, url, kind: "pull-request" }])}
verification: ${JSON.stringify({ state: "verified", evidenceRefs: [`pull-request:${pr}`] })}
visibility: public-safe
publicationEligibility: eligible
---
# ${title}

${summary}
`;
}

async function main() {
  if (process.argv.length === 3 && process.argv[2] === "--help") {
    process.stdout.write(HELP);
    return;
  }
  const { values, richRecordRefs } = argumentsFrom(process.argv.slice(2));
  const root = process.cwd();
  await readFile(join(root, "CONTEXT.md"));
  await readFile(join(root, "docs/contracts/engineering-pull-request-receipt.schema.json"));
  const pr = Number(values.get("--pr"));
  if (!Number.isSafeInteger(pr) || pr < 1) fail("PR number must be a positive safe integer.");
  const title = oneLine(values.get("--title"), "Title", 160);
  const summary = oneLine(values.get("--summary"), "Summary", 280);
  if (/^#{1,6}\s/u.test(summary)) fail("Summary must be a factual paragraph, not a Markdown heading.");
  requirePublicSafe(title, summary);
  const classification = values.get("--classification");
  if (!CLASSIFICATIONS.has(classification)) fail("Classification is not part of the Engineering receipt contract.");
  if (richRecordRefs.length > 16) fail("A receipt cannot link more than 16 rich Engineering records.");
  if (richRecordRefs.some((reference) => reference.length > 180 || !RECORD_REF.test(reference))) {
    fail("Every rich-record reference must use the exact id@revision format.");
  }
  if (new Set(richRecordRefs).size !== richRecordRefs.length) fail("Rich-record references must be unique.");
  const sortedRefs = [...richRecordRefs].sort();
  if (classification === "none" && sortedRefs.length > 0) fail("Classification none cannot link a rich Engineering record.");
  if (classification !== "none" && sortedRefs.length === 0) fail("A material classification requires at least one exact rich-record reference.");
  const directory = join(root, "docs", "engineering", "changes");
  await mkdir(directory, { recursive: true });
  const relativePath = `docs/engineering/changes/pr-${pr}.md`;
  await writeFile(join(root, relativePath), render({
    pr,
    title,
    summary,
    classification,
    richRecordRefs: sortedRefs,
  }), { encoding: "utf8", flag: "wx", mode: 0o644 });
  process.stdout.write(`Created ${relativePath}\n`);
}

main().catch((error) => {
  process.stderr.write(`Error: ${error.message}\n`);
  process.exitCode = 1;
});
