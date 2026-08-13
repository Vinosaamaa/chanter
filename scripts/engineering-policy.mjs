const CLASSIFICATIONS = new Map([
  ["none", "none"],
  ["change note", "change-note"],
  ["adr", "adr"],
  ["architecture review", "architecture-review"],
  ["feature retrospective", "feature-retrospective"],
  ["postmortem", "postmortem"],
  ["capability dossier", "capability-dossier"],
]);
const RECEIPT_FIELDS = new Set([
  "schemaVersion", "repository", "pr", "title", "classification",
  "richRecordRefs", "reconstructed", "confidence", "unknowns", "headCommit",
  "mergeCommit", "mergedAt", "sources", "verification", "visibility",
  "publicationEligibility",
]);
const RECORD_FIELDS = new Set([
  "schemaVersion", "id", "revision", "type", "status", "title", "repository",
  "capabilityIds", "createdAt", "reconstructed", "confidence", "unknowns",
  "modules", "interfaces", "seams", "adapters", "relatedRecords", "decisions",
  "incidents", "features", "capabilities", "amends", "supersedes", "learningRefs",
  "sources", "verification", "visibility", "publicationEligibility", "issue", "pr",
  "release", "run",
]);
const RECORD_TYPES = new Set([...CLASSIFICATIONS.values()].filter((value) => value !== "none"));
const RECORD_REF = /^[a-z0-9]+(?:-[a-z0-9]+)*@[1-9]\d*$/u;
const TYPE_STATUSES = new Map([
  ["change-note", new Set(["released"])],
  ["adr", new Set(["proposed", "accepted"])],
  ["architecture-review", new Set(["proposed", "accepted", "closed"])],
  ["feature-retrospective", new Set(["released"])],
  ["postmortem", new Set(["closed"])],
  ["capability-dossier", new Set(["accepted", "released"])],
]);
const PUBLIC_UNSAFE_PATTERNS = [
  /(?:^|[\s("'`])\/(?:Users|home|root)\/[^\s)"'`]+/mu,
  /(?:^|[\s("'`])\/(?:private\/tmp|tmp|var|opt|srv|workspace|mnt|Volumes)\/[^\s)"'`]+/mu,
  /(?:^|[\s("'`])~\/[^\s)"'`]+/mu,
  /\b[A-Za-z]:\\[^\s"'`]+/u,
  /\\\\[^\s\\]+\\[^\s"'`]+/u,
  /\bgh[pousr]_[A-Za-z0-9]{20,}\b/u,
  /\bsk-[A-Za-z0-9_-]{20,}\b/u,
  /\bAKIA[0-9A-Z]{16}\b/u,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/u,
  /\b(?:password|access[_-]?token|api[_-]?key|client[_-]?secret)\s*[:=]\s*[^\s]{8,}/iu,
  /\b(?:thread|task)_[A-Za-z0-9_-]{8,}\b/u,
  /\bgit@[A-Za-z0-9.-]+:[^\s]+/u,
  /https?:\/\/[^\s/@:]+:[^\s/@]+@[^\s/]+/u,
  /\b[A-Z0-9._%+-]+@(?!example\.com\b)[A-Z0-9.-]+\.[A-Z]{2,}\b/iu,
];
const HISTORICAL_SCHEMA = JSON.parse(readFileSync(new URL(
  "../docs/contracts/engineering-historical-backfill-batch.schema.json",
  import.meta.url,
), "utf8"));
const HISTORICAL_AUTHORIZATION = "I authorize publication of this bounded historical Engineering backfill batch under the residual-link policy.";

function fail(message) {
  throw new Error(message);
}

function assertPublicSafe(...values) {
  if (values.some((value) => PUBLIC_UNSAFE_PATTERNS.some((pattern) => pattern.test(String(value))))) {
    fail("Engineering document is not public-safe.");
  }
}

export function parseFrontmatter(markdown, kind = "Engineering document") {
  const normalized = markdown.replaceAll("\r\n", "\n");
  const match = normalized.match(/^---\n([\s\S]*?)\n---(?:\n|$)([\s\S]*)$/u);
  if (!match) fail(`${kind} must begin with closed frontmatter.`);
  const fields = {};
  for (const line of match[1].split("\n")) {
    if (!line || !line.includes(":")) fail(`${kind} frontmatter is invalid.`);
    const separator = line.indexOf(":");
    const key = line.slice(0, separator).trim();
    const raw = line.slice(separator + 1).trim();
    if (!/^[A-Za-z][A-Za-z0-9]*$/u.test(key) || key in fields || !raw) fail(`${kind} frontmatter is invalid.`);
    if (/^['|>&*!]/u.test(raw)) fail(`${kind} uses unsupported YAML-only syntax.`);
    if (/^["[{]/u.test(raw) || ["true", "false", "null"].includes(raw) || /^-?\d+$/u.test(raw)) {
      try { fields[key] = JSON.parse(raw); } catch { fail(`${kind} frontmatter has invalid JSON-compatible values.`); }
    } else fields[key] = raw;
  }
  return { fields, body: match[2].trim() };
}

export function selectedImpact(body) {
  const selected = [];
  let inSection = false;
  let fence = null;
  for (const line of body.split("\n")) {
    const marker = line.match(/^\s*(`{3,}|~{3,})/u)?.[1];
    if (fence) {
      if (marker?.[0] === fence[0] && marker.length >= fence.length) fence = null;
      continue;
    }
    if (marker) { fence = marker; continue; }
    const heading = line.match(/^##\s+(.+?)\s*$/u);
    if (heading) { inSection = heading[1].toLowerCase() === "engineering impact"; continue; }
    if (!inSection) continue;
    const choice = line.match(/^\s*-\s*\[[xX]\]\s*(None|Change Note|ADR|Architecture Review|Feature Retrospective|Postmortem|Capability Dossier)(?:\s*[—-]\s*reason:\s*(.*))?\s*$/iu);
    if (choice) selected.push({ classification: CLASSIFICATIONS.get(choice[1].toLowerCase()), reason: (choice[2] ?? "").trim() });
  }
  if (selected.length !== 1) fail("Select exactly one Engineering impact classification in the pull request body.");
  return selected[0];
}

export function parseReceipt(markdown) {
  const { fields, body } = parseFrontmatter(markdown, "Pull Request Receipt");
  if (Object.keys(fields).length !== RECEIPT_FIELDS.size || Object.keys(fields).some((key) => !RECEIPT_FIELDS.has(key))) {
    fail("Pull Request Receipt fields do not match contract v1.");
  }
  const blocks = body.split(/\n\s*\n/u).filter(Boolean);
  if (blocks.length !== 2 || blocks[0] !== `# ${fields.title}` || !blocks[1] || [...blocks[1]].length > 280) {
    fail("Pull Request Receipt must contain its exact title and one summary paragraph of at most 280 characters.");
  }
  if (fields.schemaVersion !== 1 || typeof fields.repository !== "string" || !/^[A-Za-z0-9_.-]+$/u.test(fields.repository) ||
      !Number.isSafeInteger(fields.pr) || fields.pr < 1 || typeof fields.title !== "string" || !fields.title || fields.title.length > 160 ||
      !["none", ...RECORD_TYPES].includes(fields.classification) || typeof fields.reconstructed !== "boolean" ||
      !["verified", "high", "medium", "low", "unknown"].includes(fields.confidence) ||
      fields.visibility !== "public-safe" || fields.publicationEligibility !== "eligible") {
    fail("Pull Request Receipt identity or bounded scalar fields are invalid.");
  }
  boundedStrings(fields.richRecordRefs, "Pull Request Receipt richRecordRefs", 16, 180);
  if (fields.richRecordRefs.some((reference) => !RECORD_REF.test(reference)) ||
      (fields.classification === "none" ? fields.richRecordRefs.length !== 0 : fields.richRecordRefs.length === 0)) {
    fail("Pull Request Receipt richRecordRefs are invalid for its classification.");
  }
  boundedStrings(fields.unknowns, "Pull Request Receipt unknowns");
  for (const key of ["headCommit", "mergeCommit"]) {
    if (fields[key] !== null && (typeof fields[key] !== "string" || !/^[0-9a-f]{40}$/u.test(fields[key]))) {
      fail(`Pull Request Receipt ${key} is invalid.`);
    }
  }
  if (fields.mergedAt !== null && (typeof fields.mergedAt !== "string" ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/u.test(fields.mergedAt) || Number.isNaN(Date.parse(fields.mergedAt)))) {
    fail("Pull Request Receipt mergedAt is invalid.");
  }
  validateSources(fields.sources, "Pull Request Receipt sources");
  validateVerification(fields.verification, fields.confidence, "Pull Request Receipt");
  assertPublicSafe(JSON.stringify(fields), body);
  return { ...fields, summary: blocks[1] };
}

function boundedStrings(value, label, maxItems = 32, maxLength = 512) {
  if (!Array.isArray(value) || value.length > maxItems ||
      value.some((item) => typeof item !== "string" || !item || item.length > maxLength) || new Set(value).size !== value.length) {
    fail(`${label} must be bounded unique non-empty strings.`);
  }
  return value;
}

function validateSources(value, label) {
  const kinds = new Set(["issue", "pull-request", "commit", "release", "run", "documentation"]);
  if (!Array.isArray(value) || value.length < 1 || value.length > 32 || value.some((source) =>
    !source || typeof source !== "object" || Array.isArray(source) ||
    Object.keys(source).sort().join(",") !== "kind,label,url" ||
    typeof source.label !== "string" || !source.label || source.label.length > 160 ||
    typeof source.url !== "string" || source.url.length > 2048 || !/^https:\/\/[^\s]+$/u.test(source.url) ||
    !kinds.has(source.kind)
  )) fail(`${label} are invalid.`);
  return value;
}

function validateVerification(value, confidence, label) {
  if (!value || typeof value !== "object" || Array.isArray(value) ||
      Object.keys(value).sort().join(",") !== "evidenceRefs,state" ||
      !["verified", "not-recorded"].includes(value.state)) fail(`${label} verification is invalid.`);
  boundedStrings(value.evidenceRefs, `${label} verification evidenceRefs`);
  if (confidence === "verified" && (value.state !== "verified" || value.evidenceRefs.length === 0)) {
    fail(`Verified ${label} requires verified evidence.`);
  }
  return value;
}

export function parseRecord(path, markdown) {
  const { fields, body } = parseFrontmatter(markdown, "Engineering record");
  const keys = Object.keys(fields);
  if (keys.some((key) => !RECORD_FIELDS.has(key) && key !== "diagrams") || [...RECORD_FIELDS].some((key) => !(key in fields))) {
    fail("Engineering record fields do not match contract v1.");
  }
  if (fields.schemaVersion !== 1 || fields.repository !== "chanter" || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/u.test(fields.id)) {
    fail("Engineering record identity is invalid.");
  }
  if (!Number.isInteger(fields.revision) || fields.revision < 1 || !RECORD_TYPES.has(fields.type)) fail("Engineering record revision or type is invalid.");
  if (!["proposed", "accepted", "released", "closed"].includes(fields.status) || !TYPE_STATUSES.get(fields.type)?.has(fields.status)) {
    fail("Engineering record status is invalid for its type.");
  }
  if (fields.visibility !== "public-safe" || fields.publicationEligibility !== "eligible") fail("Engineering record is not public-safe and eligible.");
  if (typeof fields.reconstructed !== "boolean") fail("Engineering record reconstructed field is invalid.");
  if (typeof fields.title !== "string" || !fields.title || fields.title.length > 160 ||
      typeof fields.createdAt !== "string" || !/^\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}Z)?$/u.test(fields.createdAt) ||
      Number.isNaN(Date.parse(fields.createdAt.length === 10 ? `${fields.createdAt}T00:00:00Z` : fields.createdAt)) ||
      !["verified", "high", "medium", "low", "unknown"].includes(fields.confidence)) {
    fail("Engineering record bounded scalar fields are invalid.");
  }
  for (const key of [
    "capabilityIds", "unknowns", "modules", "interfaces", "seams", "adapters",
    "relatedRecords", "decisions", "incidents", "features", "capabilities", "amends", "supersedes", "learningRefs",
  ]) boundedStrings(fields[key], `Engineering record ${key}`);
  if (fields.learningRefs.length) fail("Chanter Engineering record learningRefs must remain empty in contract v1.");
  for (const key of ["relatedRecords", "decisions", "incidents", "features", "amends", "supersedes"]) {
    if (fields[key].some((reference) => !RECORD_REF.test(reference))) fail(`Engineering record ${key} contains an invalid exact reference.`);
  }
  validateSources(fields.sources, "Engineering record sources");
  validateVerification(fields.verification, fields.confidence, "Engineering record");
  for (const key of ["issue", "pr"]) {
    if (fields[key] !== null && (!Number.isSafeInteger(fields[key]) || fields[key] < 1)) fail(`Engineering record ${key} is invalid.`);
  }
  for (const key of ["release", "run"]) {
    if (fields[key] !== null && (typeof fields[key] !== "string" || !fields[key] || fields[key].length > 512)) {
      fail(`Engineering record ${key} is invalid.`);
    }
  }
  if (fields.diagrams !== undefined && (!Array.isArray(fields.diagrams) || fields.diagrams.length > 16 || fields.diagrams.some((diagram) => {
    if (!diagram || typeof diagram !== "object" || Array.isArray(diagram) ||
        Object.keys(diagram).sort().join(",") !== "evidenceRefs,renderedPath,sourcePath,summary,title" ||
        typeof diagram.title !== "string" || !diagram.title || diagram.title.length > 160 ||
        typeof diagram.summary !== "string" || !diagram.summary || diagram.summary.length > 280 ||
        typeof diagram.sourcePath !== "string" || !/^docs\/design\/[A-Za-z0-9._/-]+\.drawio$/u.test(diagram.sourcePath) || diagram.sourcePath.includes("..") ||
        typeof diagram.renderedPath !== "string" || !/^docs\/design\/[A-Za-z0-9._/-]+\.(?:png|svg)$/u.test(diagram.renderedPath) || diagram.renderedPath.includes("..")) return true;
    try { return boundedStrings(diagram.evidenceRefs, "Engineering diagram evidenceRefs").some((ref) => !fields.verification.evidenceRefs.includes(ref)); }
    catch { return true; }
  }))) {
    fail("Engineering record diagrams are invalid.");
  }
  if (!body.startsWith(`# ${fields.title}\n`) && body !== `# ${fields.title}`) fail("Engineering record body title must match frontmatter.");
  assertPublicSafe(JSON.stringify(fields), body);
  const expectedPath = `docs/engineering/records/${fields.id}.md`;
  if (path !== expectedPath) fail("Engineering record path must match its id.");
  return { ...fields, ref: `${fields.id}@${fields.revision}`, path, body };
}

export function validatePullRequest(input) {
  const impact = selectedImpact(input.body ?? "");
  const expectedPath = `docs/engineering/changes/pr-${input.number}.md`;
  const receipts = input.changedFiles.filter((path) => path.startsWith("docs/engineering/changes/"));
  if (receipts.length !== 1 || receipts[0] !== expectedPath) fail(`Every pull request must change exactly ${expectedPath}.`);
  const receipt = parseReceipt(input.receiptMarkdown);
  if (receipt.schemaVersion !== 1 || receipt.repository !== "chanter" || receipt.pr !== input.number || receipt.title !== input.title) {
    fail("Pull Request Receipt identity must match this Chanter pull request.");
  }
  if (receipt.reconstructed !== false || receipt.visibility !== "public-safe" || receipt.publicationEligibility !== "eligible") {
    fail("Forward Pull Request Receipt must be public-safe, eligible, and reconstructed false.");
  }
  if (receipt.classification !== impact.classification) fail("Pull Request Receipt classification must match the pull request body.");
  if (!Array.isArray(receipt.richRecordRefs) || receipt.richRecordRefs.some((reference) => !RECORD_REF.test(reference))) {
    fail("Pull Request Receipt richRecordRefs are invalid.");
  }
  if (impact.classification === "none") {
    const normalizedReason = impact.reason.trim().replace(/^[\p{P}\p{S}\s]+|[\p{P}\p{S}\s]+$/gu, "").toLowerCase();
    if (impact.reason.length < 12 || !/[A-Za-z]{2}/u.test(impact.reason) ||
        /^(?:todo|tbd|n\/?a|none|replace\b)/u.test(normalizedReason)) {
      fail("Engineering impact None requires a concrete reason.");
    }
    if (receipt.richRecordRefs.length || input.changedRecordPaths.length) fail("Engineering impact None cannot link or change rich records.");
    return { classification: impact.classification, receipt, recordRefs: [] };
  }
  const records = input.recordsAtHead.map(({ path, markdown }) => parseRecord(path, markdown));
  const byRef = new Map(records.map((record) => [record.ref, record]));
  if (receipt.richRecordRefs.length === 0) fail("Material Engineering receipt requires an exact rich-record reference.");
  for (const reference of receipt.richRecordRefs) {
    const record = byRef.get(reference);
    if (!record) fail(`Material Engineering receipt reference does not resolve at head: ${reference}.`);
    if (record.type !== impact.classification) fail("Material Engineering receipt type must match its linked records.");
  }
  if (input.changedRecordPaths.some((path) => input.baseRecordPaths.includes(path))) {
    fail("Accepted Engineering record revisions are immutable; add an amendment or superseding record.");
  }
  const changedRefs = records.filter((record) => input.changedRecordPaths.includes(record.path)).map((record) => record.ref);
  if (records.some((record) => input.changedRecordPaths.includes(record.path) && record.reconstructed !== false)) {
    fail("Forward-authored rich records must set reconstructed false.");
  }
  if (changedRefs.some((reference) => !receipt.richRecordRefs.includes(reference))) fail("Receipt must link every rich record changed by this pull request.");
  return { classification: impact.classification, receipt, recordRefs: receipt.richRecordRefs };
}

function exactSet(left, right) {
  return left.length === new Set(left).size && right.length === new Set(right).size &&
    left.length === right.length && [...left].sort().every((value, index) => value === [...right].sort()[index]);
}

function assertHistoricalManifest(manifest, input) {
  const properties = HISTORICAL_SCHEMA.properties;
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest) ||
      !exactSet(Object.keys(manifest), HISTORICAL_SCHEMA.required) ||
      manifest.schemaVersion !== 1 || manifest.repository !== "chanter" ||
      manifest.pullRequest !== input.pullRequestNumber ||
      !Number.isInteger(manifest.pullRequest) || manifest.pullRequest < 1) {
    fail("Historical batch manifest identity or fields are invalid.");
  }
  const boundedRefs = (value, property) => Array.isArray(value) && value.length <= property.maxItems &&
    value.length === new Set(value).size && value.every((reference) =>
      typeof reference === "string" && reference.length <= property.items.maxLength && RECORD_REF.test(reference));
  if (!Array.isArray(manifest.receiptPaths) || manifest.receiptPaths.length < 1 ||
      manifest.receiptPaths.length > properties.receiptPaths.maxItems ||
      manifest.receiptPaths.length !== new Set(manifest.receiptPaths).size ||
      manifest.receiptPaths.some((path) => typeof path !== "string" || path.length > properties.receiptPaths.items.maxLength ||
        !/^docs\/engineering\/changes\/pr-[1-9]\d*\.md$/u.test(path)) ||
      !boundedRefs(manifest.recordRefs, properties.recordRefs) ||
      !boundedRefs(manifest.addedRecordRefs, properties.addedRecordRefs)) {
    fail("Historical batch manifest exceeds its bounded contract.");
  }
  const expectedManifestPath = `docs/engineering/backfill/pr-${input.pullRequestNumber}.json`;
  if (input.manifestPath !== expectedManifestPath ||
      typeof manifest.privacyAuthorizationUrl !== "string" ||
      manifest.privacyAuthorizationUrl.length > properties.privacyAuthorizationUrl.maxLength ||
      !manifest.privacyAuthorizationUrl.startsWith(`https://github.com/${input.repositoryFullName}/`) ||
      !/#issuecomment-[1-9]\d*$/u.test(manifest.privacyAuthorizationUrl)) {
    fail("Historical batch manifest path or authorization URL is invalid.");
  }
  if (manifest.receiptPaths.includes(`docs/engineering/changes/pr-${input.pullRequestNumber}.md`)) {
    fail("Historical batch cannot claim its forward pull request receipt.");
  }
}

export function validateHistoricalBatch(input) {
  assertHistoricalManifest(input.manifest, input);
  const comment = input.authorizationComment;
  if (comment?.html_url !== input.manifest.privacyAuthorizationUrl || comment?.author_association !== "OWNER" ||
      comment?.body?.trim() !== HISTORICAL_AUTHORIZATION) {
    fail("Historical batch requires exact repository-owner authorization.");
  }
  if (input.baseExistingPaths.length) fail("Historical batch documents are add-only.");

  const receipts = input.historicalReceipts.map(({ path, markdown }) => ({ path, ...parseReceipt(markdown) }));
  if (!exactSet(input.manifest.receiptPaths, receipts.map(({ path }) => path))) {
    fail("Historical batch manifest must enumerate its reconstructed receipts exactly.");
  }
  for (const receipt of receipts) {
    const match = receipt.path.match(/^docs\/engineering\/changes\/pr-([1-9]\d*)\.md$/u);
    if (!match || receipt.pr !== Number(match[1]) || receipt.repository !== "chanter" || receipt.reconstructed !== true) {
      fail("Historical receipt identity is invalid.");
    }
    if (receipt.classification === "none" ? receipt.richRecordRefs.length !== 0 : receipt.richRecordRefs.length === 0) {
      fail("Historical receipt classification and rich-record references disagree.");
    }
  }

  const records = input.recordsAtHead.map(({ path, markdown }) => parseRecord(path, markdown));
  const byRef = new Map(records.map((record) => [record.ref, record]));
  const referenced = [...new Set(receipts.flatMap((receipt) => receipt.richRecordRefs))];
  if (!exactSet(input.manifest.recordRefs, referenced)) fail("Historical batch manifest must enumerate referenced rich records exactly.");
  for (const receipt of receipts) {
    if (receipt.richRecordRefs.some((reference) => !byRef.has(reference) || byRef.get(reference).type !== receipt.classification)) {
      fail("Historical receipt has a missing or mismatched exact rich-record reference.");
    }
  }
  const added = records.filter((record) => input.changedRecordPaths.includes(record.path));
  if (added.some((record) => record.reconstructed !== true)) fail("Historical rich records must set reconstructed true.");
  if (!exactSet(input.manifest.addedRecordRefs, added.map((record) => record.ref)) ||
      added.some((record) => !referenced.includes(record.ref))) {
    fail("Historical batch rich records must be declared and linked exactly.");
  }
  const allowedFiles = [
    `docs/engineering/changes/pr-${input.pullRequestNumber}.md`,
    input.manifestPath,
    ...input.manifest.receiptPaths,
    ...input.changedRecordPaths,
  ];
  if (!exactSet(input.changedFiles, allowedFiles)) fail("Historical batch contains files outside its bounded manifest.");
  return { historicalReceiptCount: receipts.length, historicalRecordCount: added.length };
}
import { readFileSync } from "node:fs";
