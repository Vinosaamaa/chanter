---
schemaVersion: 1
id: feature-retrospective-chanter-product-readiness
revision: 1
type: feature-retrospective
status: released
title: Trustworthy release gates for Chanter's local beta
repository: chanter
capabilityIds: ["trustworthy-release-gates"]
createdAt: 2026-08-09
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and complete workflow logs were not quoted.","No public launch was evidenced; release truth remains strong local beta.","Sensitive source values and nonessential risky evidence links were omitted from this reconstructed record."]
modules: ["backend build","frontend release tooling","product test stack","GitHub Actions"]
interfaces: ["repository test commands","pull-request CI","signed-in browser release gate","dependency review and audits","test artifact retention"]
seams: ["local environment to hermetic test process","repository Java runtime to Maven lifecycle","browser journeys to full product stack","source changes to CI evidence"]
adapters: ["scripts/testing/run-hermetic.sh","scripts/java21.sh","frontend release fixture","bundle-budget checker","product demo seed","CI workflow"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Hermetic backend and product tests","Java 21 enforcement","Dependency and bundle regression gates","Signed-in Owner, Member, and Learner browser journeys","Retained failure diagnostics"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Issue #241","url":"https://github.com/Vinosaamaa/chanter/issues/241","kind":"issue"},{"label":"Pull request #259","url":"https://github.com/Vinosaamaa/chanter/pull/259","kind":"pull-request"},{"label":"PR #259 head commit","url":"https://github.com/Vinosaamaa/chanter/commit/30e450add6250ce08c35f5d10f1db386f7780e16","kind":"commit"},{"label":"PR #259 merge commit","url":"https://github.com/Vinosaamaa/chanter/commit/2f830ec4367e26cdf4d30d5f3fa33d526c99538c","kind":"commit"},{"label":"PR #259 CI run","url":"https://github.com/Vinosaamaa/chanter/actions/runs/31324411951","kind":"run"},{"label":"Issue #241 change log at merge","url":"https://github.com/Vinosaamaa/chanter/blob/2f830ec4367e26cdf4d30d5f3fa33d526c99538c/docs/operations/issue-241-change-log.md","kind":"documentation"}]
verification: {"state":"verified","evidenceRefs":["issue:241","pull-request:259","head-commit:30e450add6250ce08c35f5d10f1db386f7780e16","merge-commit:2f830ec4367e26cdf4d30d5f3fa33d526c99538c","run:31324411951","documentation:issue-241-change-log"]}
visibility: public-safe
publicationEligibility: eligible
issue: 241
pr: 259
release: null
run: "31324411951"
---
# Trustworthy release gates for Chanter's local beta

## Context

Issue #241 treated a green check as meaningful only when the command, runtime, dependencies, browser personas, and retained diagnostics were deterministic. Earlier checks could inherit local configuration, use an incompatible Java runtime, or prove only that a public route rendered. Those gaps made the signal too weak for the product-readiness program.

## Gate design

PR #259 established a hermetic process boundary for backend and product tests, selected and enforced Java 21, and kept Maven verification as the backend lifecycle. It added dependency review and audits, lockfile-reproducible frontend installation, and an enforced JavaScript and CSS bundle budget.

The browser boundary expanded from public-route presence to settled page content and authorization-aware Owner, membership-only Member, and enrolled Learner journeys. The release fixture checks browser exceptions, console failures, failed requests, first-party HTTP failures, and horizontal overflow. CI retains backend, browser, and product-service diagnostics for failed investigations.

## Evidence from the implementation loop

The issue change log records red-green cycles for environment isolation, Java selection, bundle budgets, and the membership-only persona. The debug record explains why the tests initially exposed inherited runtime configuration, implicit Java selection, Maven invocation context, missing persona data, test-discovery overlap, dependency-graph configuration, and a frontend-only request to an absent backend.

The final PR head passed dependency review, backend, frontend, and signed-in product jobs. The merged change log records successful backend, frontend, dependency, bundle, public browser, signed-in browser, Compose, and product-health verification.

## Product-truth boundary

These gates improve confidence in a strong local beta; they do not prove that a public environment exists, that production cutover occurred, or that launch sign-off was granted. Provider-backed infrastructure, operations, and public-cutover evidence remain separate work.

## Public-safety boundary

The canonical prose omits contributor contact details, local configuration values, and other nonessential source metadata. The required PR link is retained only under the repository owner's batch-specific residual-link authorization. This keeps record safety—the contents published here—separate from source risk carried by linked historical pages.

## Outcome

Chanter gained repeatable release evidence across build, dependency, browser, and full-stack seams. Failures became more actionable because the same policy that blocks regressions also preserves bounded diagnostics, while the public narrative remains careful not to overstate deployment readiness.
