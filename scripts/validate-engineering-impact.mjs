#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

import { validateHistoricalBatch, validatePullRequest } from "./engineering-policy.mjs";

const SHA = /^[0-9a-f]{40}$/u;

function fail(message) {
  throw new Error(message);
}

function git(arguments_, options = {}) {
  try {
    return execFileSync("git", arguments_, {
      encoding: options.encoding ?? "utf8",
      maxBuffer: 16 * 1024 * 1024,
      stdio: ["ignore", "pipe", "pipe"],
    });
  } catch {
    fail("Required pull request Git objects are unavailable or invalid.");
  }
}

function gitText(arguments_) {
  return git(arguments_).trim();
}

function pathsAt(revision, prefix) {
  const output = gitText(["ls-tree", "-r", "--name-only", revision, "--", prefix]);
  return output ? output.split("\n").filter(Boolean) : [];
}

function documentAt(revision, path) {
  return gitText(["show", `${revision}:${path}`]);
}

function changedPaths(base, head) {
  const output = gitText(["diff", "--name-only", "--diff-filter=ACMRTD", base, head, "--"]);
  return output ? output.split("\n").filter(Boolean) : [];
}

function pathExistsAt(revision, path) {
  try {
    execFileSync("git", ["cat-file", "-e", `${revision}:${path}`], {
      stdio: ["ignore", "ignore", "ignore"],
    });
    return true;
  } catch {
    return false;
  }
}

function authorizationComment(repositoryFullName, url) {
  const match = url.match(/#issuecomment-([1-9]\d*)$/u);
  if (!match) fail("Historical batch authorization URL is invalid.");
  try {
    return JSON.parse(execFileSync("gh", ["api", `repos/${repositoryFullName}/issues/comments/${match[1]}`], {
      encoding: "utf8",
      maxBuffer: 1024 * 1024,
      stdio: ["ignore", "pipe", "pipe"],
    }));
  } catch {
    fail("Unable to verify the historical batch owner authorization.");
  }
}

export function validateEvent(event, cwd = process.cwd()) {
  if (cwd !== process.cwd()) process.chdir(cwd);
  const pullRequest = event?.pull_request;
  const number = pullRequest?.number;
  const base = pullRequest?.base?.sha;
  const head = pullRequest?.head?.sha;
  if (event?.repository?.name !== "chanter" || !Number.isInteger(number) || !SHA.test(base ?? "") || !SHA.test(head ?? "")) {
    fail("Pull request event identity is invalid.");
  }
  git(["cat-file", "-e", `${base}^{commit}`]);
  git(["cat-file", "-e", `${head}^{commit}`]);

  const changedFiles = changedPaths(base, head);
  const receiptPath = `docs/engineering/changes/pr-${number}.md`;
  const changedRecordPaths = changedFiles.filter((path) => path.startsWith("docs/engineering/records/") && path.endsWith(".md"));
  const recordPaths = pathsAt(head, "docs/engineering/records");
  const recordsAtHead = recordPaths.map((path) => ({ path, markdown: documentAt(head, path) }));
  const manifestPaths = changedFiles.filter((path) => path.startsWith("docs/engineering/backfill/") && path.endsWith(".json"));
  let historical;
  let forwardChangedFiles = changedFiles;
  let forwardChangedRecordPaths = changedRecordPaths;
  if (manifestPaths.length > 0) {
    if (manifestPaths.length !== 1) fail("A historical publication pull request requires exactly one batch manifest.");
    let manifest;
    try { manifest = JSON.parse(documentAt(head, manifestPaths[0])); } catch { fail("Historical batch manifest JSON is invalid."); }
    const repositoryFullName = event.repository.full_name ?? "Vinosaamaa/chanter";
    const historicalReceiptPaths = Array.isArray(manifest.receiptPaths) ? manifest.receiptPaths : [];
    const candidateHistoricalPaths = [manifestPaths[0], ...historicalReceiptPaths, ...changedRecordPaths];
    historical = validateHistoricalBatch({
      manifest,
      manifestPath: manifestPaths[0],
      pullRequestNumber: number,
      repositoryFullName,
      changedFiles,
      historicalReceipts: historicalReceiptPaths.map((path) => ({ path, markdown: documentAt(head, path) })),
      recordsAtHead,
      changedRecordPaths,
      baseExistingPaths: candidateHistoricalPaths.filter((path) => pathExistsAt(base, path)),
      authorizationComment: authorizationComment(repositoryFullName, manifest.privacyAuthorizationUrl ?? ""),
    });
    const historicalSet = new Set([manifestPaths[0], ...historicalReceiptPaths, ...changedRecordPaths]);
    forwardChangedFiles = changedFiles.filter((path) => !historicalSet.has(path));
    forwardChangedRecordPaths = [];
  }
  const result = validatePullRequest({
    number,
    title: pullRequest.title ?? "",
    body: pullRequest.body ?? "",
    changedFiles: forwardChangedFiles,
    receiptMarkdown: documentAt(head, receiptPath),
    recordsAtHead,
    changedRecordPaths: forwardChangedRecordPaths,
    baseRecordPaths: pathsAt(base, "docs/engineering/records"),
  });
  return { ...result, changedFileCount: changedFiles.length, historical };
}

function main() {
  const eventPath = process.argv[2] ?? process.env.GITHUB_EVENT_PATH;
  if (!eventPath) fail("Usage: node scripts/validate-engineering-impact.mjs <pull-request-event.json>");
  const event = JSON.parse(readFileSync(eventPath, "utf8"));
  const result = validateEvent(event);
  const historical = result.historical
    ? `; ${result.historical.historicalReceiptCount} reconstructed receipt(s), ${result.historical.historicalRecordCount} reconstructed record(s)`
    : "";
  process.stdout.write(`Engineering impact: ${result.classification}; receipt PR #${result.receipt.pr}; ${result.changedFileCount} changed file(s)${historical}.\n`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main();
  } catch (error) {
    process.stderr.write(`Engineering policy failed: ${error instanceof Error ? error.message : "unknown error"}\n`);
    process.exitCode = 1;
  }
}
