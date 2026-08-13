#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, relative, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { parseReceipt, parseRecord } from "./engineering-policy.mjs";

const OWNER = "Vinosaamaa";
const REPOSITORY = "chanter";

function markdownFiles(directory) {
  try {
    return readdirSync(directory, { withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
      .map((entry) => join(directory, entry.name))
      .sort();
  } catch (error) {
    if (error?.code === "ENOENT") return [];
    throw error;
  }
}

function sourceEvidence(root, path) {
  let output;
  try {
    output = execFileSync("git", ["log", "-1", "--format=%H%x00%cI", "--", path], {
      cwd: root,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch {
    throw new Error("Every canonical Engineering document must be committed before projection.");
  }
  const [commit, committedAt] = output.split("\0");
  if (!/^[0-9a-f]{40}$/u.test(commit ?? "") || !committedAt) {
    throw new Error("Every canonical Engineering document must have immutable Git source evidence.");
  }
  return {
    sourceCommit: commit,
    sourceCommittedAt: committedAt,
    sourcePath: path,
    sourceUrl: `https://github.com/${OWNER}/${REPOSITORY}/blob/${commit}/${path}`,
  };
}

function countBy(values) {
  return Object.fromEntries([...new Set(values)].sort().map((value) => [value, values.filter((item) => item === value).length]));
}

function escapeHtml(value) {
  return String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
}

function portableHtml(index) {
  const receipts = index.pullRequestReceipts.map((receipt) => `<article id="pr-${receipt.pr}">
<h3>PR #${receipt.pr}: ${escapeHtml(receipt.title)}</h3>
<p>${escapeHtml(receipt.summary)}</p>
<p><strong>${escapeHtml(receipt.classification)}</strong> · <a href="${escapeHtml(receipt.sourceUrl)}">immutable source</a></p>
</article>`).join("\n");
  const records = index.records.map((record) => `<article id="${escapeHtml(record.ref)}">
<h3>${escapeHtml(record.title)}</h3>
<p><strong>${escapeHtml(record.ref)}</strong> · ${escapeHtml(record.type)} · ${escapeHtml(record.status)}</p>
<pre>${escapeHtml(record.body)}</pre>
<p><a href="${escapeHtml(record.sourceUrl)}">immutable source</a></p>
</article>`).join("\n");
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Portable Chanter Engineering evidence</title>
<style>body{font-family:ui-sans-serif,system-ui;max-width:76rem;margin:auto;padding:2rem;color:#171717}article{border-top:1px solid #bbb;padding:1rem 0}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#f4f4f4;padding:1rem}a{color:#0645ad}</style></head>
<body><h1>Portable Chanter Engineering evidence</h1><p>Generated from canonical public-safe Markdown at immutable Git commits. Product status: ${escapeHtml(index.productStatus)}.</p>
<h2>All merged pull requests</h2>${receipts || "<p>No receipts.</p>"}
<h2>Rich records</h2>${records || "<p>No rich records.</p>"}
</body></html>\n`;
}

export function buildEngineeringJournal({ root = process.cwd() } = {}) {
  const canonicalRoot = resolve(root);
  const receiptPaths = markdownFiles(join(canonicalRoot, "docs", "engineering", "changes"));
  const recordPaths = markdownFiles(join(canonicalRoot, "docs", "engineering", "records"));
  const pullRequestReceipts = receiptPaths.map((absolutePath) => {
    const path = relative(canonicalRoot, absolutePath).replaceAll("\\", "/");
    const receipt = parseReceipt(readFileSync(absolutePath, "utf8"));
    return { ...receipt, ...sourceEvidence(canonicalRoot, path) };
  }).sort((left, right) => left.pr - right.pr);
  const records = recordPaths.map((absolutePath) => {
    const path = relative(canonicalRoot, absolutePath).replaceAll("\\", "/");
    return { ...parseRecord(path, readFileSync(absolutePath, "utf8")), ...sourceEvidence(canonicalRoot, path) };
  }).sort((left, right) => left.ref.localeCompare(right.ref));
  const byRef = new Map(records.map((record) => [record.ref, record]));
  if (byRef.size !== records.length) throw new Error("Rich Engineering record revisions must be unique.");
  const backlinks = Object.fromEntries(records.map((record) => [record.ref, []]));
  for (const record of records) {
    record.amendedBy = [];
    record.supersededBy = [];
    for (const reference of [
      ...record.relatedRecords, ...record.decisions, ...record.incidents, ...record.features,
      ...record.amends, ...record.supersedes,
    ]) {
      if (!byRef.has(reference) || reference === record.ref) throw new Error("Rich Engineering record relation is missing or self-referential.");
      backlinks[reference].push(record.ref);
    }
    for (const reference of record.amends) byRef.get(reference).amendedBy.push(record.ref);
    for (const reference of record.supersedes) byRef.get(reference).supersededBy.push(record.ref);
  }
  for (const record of records) {
    record.amendedBy.sort();
    record.supersededBy.sort();
    record.effectiveStatus = record.supersededBy.length ? "superseded" : record.amendedBy.length ? "amended" : record.status;
    backlinks[record.ref].sort();
  }
  const refs = new Set(byRef.keys());
  for (const receipt of pullRequestReceipts) {
    for (const reference of receipt.richRecordRefs) {
      if (!refs.has(reference)) throw new Error(`Receipt #${receipt.pr} has an unresolved exact rich-record reference.`);
      if (byRef.get(reference).type !== receipt.classification) {
        throw new Error("Material receipt classification must match every linked rich-record type.");
      }
    }
  }
  const receiptBacklinks = Object.fromEntries(records.map((record) => [
    record.ref,
    pullRequestReceipts.filter((receipt) => receipt.richRecordRefs.includes(record.ref)).map((receipt) => receipt.pr),
  ]));
  const receiptSearch = pullRequestReceipts.map((receipt) => ({
    pr: receipt.pr,
    classification: receipt.classification,
    text: [receipt.pr, receipt.title, receipt.summary, receipt.classification, ...receipt.richRecordRefs].join(" ").toLowerCase(),
  }));
  const recordSearch = records.map((record) => ({
    ref: record.ref,
    type: record.type,
    text: [record.ref, record.title, record.repository, ...record.capabilityIds, ...record.modules,
      ...record.interfaces, ...record.capabilities, record.body].join(" ").toLowerCase(),
  }));
  const index = {
    schemaVersion: 1,
    project: REPOSITORY,
    productStatus: "strong-local-beta",
    generatedFrom: "canonical-git-markdown",
    pullRequestReceipts,
    records,
    receiptSearch,
    recordSearch,
    backlinks,
    receiptBacklinks,
    statistics: {
      pullRequestCount: pullRequestReceipts.length,
      richRecordCount: records.length,
      receiptsByClassification: countBy(pullRequestReceipts.map((receipt) => receipt.classification)),
      recordsByType: countBy(records.map((record) => record.type)),
      recordsByStatus: countBy(records.map((record) => record.effectiveStatus)),
    },
  };
  const indexJson = `${JSON.stringify(index, null, 2)}\n`;
  return { index, indexJson, portableHtml: portableHtml(index) };
}

function main() {
  const check = process.argv.includes("--check");
  if (process.argv.some((argument, index) => index > 1 && argument !== "--check")) {
    throw new Error("Usage: node scripts/build-engineering-journal.mjs [--check]");
  }
  const first = buildEngineeringJournal();
  const second = buildEngineeringJournal();
  if (first.indexJson !== second.indexJson || first.portableHtml !== second.portableHtml) {
    throw new Error("Engineering projection is not deterministic.");
  }
  const digest = createHash("sha256").update(first.indexJson).update("\0").update(first.portableHtml).digest("hex");
  if (check) {
    process.stdout.write(`Engineering projection verified: ${digest}\n`);
    return;
  }
  const output = join(process.cwd(), ".engineering-journal", "generated");
  mkdirSync(output, { recursive: true });
  writeFileSync(join(output, "index.json"), first.indexJson);
  writeFileSync(join(output, "portable.html"), first.portableHtml);
  process.stdout.write(`Generated .engineering-journal/generated/index.json and portable.html (${digest}).\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { main(); } catch (error) {
    process.stderr.write(`Engineering projection failed: ${error instanceof Error ? error.message : "unknown error"}\n`);
    process.exitCode = 1;
  }
}
