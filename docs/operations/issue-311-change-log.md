# Issue #311 change log: Portable Engineering evidence checks

**Issue:** [#311](https://github.com/Vinosaamaa/chanter/issues/311)  
**Branch:** `codex/311-engineering-windows`  
**Date:** 2026-09-11

## Outcome

Pinned the three released Engineering schema files to LF checkout bytes. The released schema content and expected SHA-256 hashes remain unchanged. Privacy fixtures now select POSIX or Windows path construction explicitly, so the same tests exercise user-home paths, drive paths, and UNC shares on either host.

The existing Ubuntu `engineering-policy` job keeps its name and validation steps. A separate `engineering-windows` job runs the authoring, validation, and projection tests on Windows with Node 22. Product code and privacy rejection patterns are unchanged.

## TDD evidence

The untouched Windows baseline failed two of fourteen tests: the released-schema hash assertion and the scaffold's intended POSIX privacy fixture. The native path join had produced a rooted Windows path without a drive, rather than the POSIX path the test intended.

Added a regression that writes the canonical Git blobs into a fixture repository, enables `core.autocrlf=true`, removes its working copies, and checks them out again. It verifies the resulting bytes against the original blobs. The regression failed before the LF attributes existed and passes with the three exact-path rules.

Expanded the public-safety cases to explicit POSIX user homes, Linux homes, Windows drives, and UNC shares. Each path is rejected in receipt metadata, receipt prose, rich-record prose, and scaffold input. The scaffold tests also verify that rejection neither echoes the unsafe input nor writes a receipt.

## Verification

- `node --test scripts/tests/engineering-*.test.mjs`: passed on Windows, 22 tests.
- `node scripts/build-engineering-journal.mjs --check`: passed locally.
- Released contract hashes: unchanged; schema files have no Git diff.
- Hosted Windows and Ubuntu Engineering checks passed at `30a6840` in [CI run 34672721325](https://github.com/Vinosaamaa/chanter/actions/runs/34672721325). Broader CI was blocked by the baseline MinIO image pull and frontend dependency audit; the final head must pass those gates before merge.

For an existing Windows checkout, Git may retain an unchanged file's old CRLF working copy until it rewrites that file. Refresh only clean contract files from Git, or use a fresh issue worktree. Do not alter the schema text or expected hashes to compensate for checkout line endings.

## Scope

This is a checkout and test portability repair. It does not change the evidence schema, privacy policy, product API, database, or deployed runtime. The compact Engineering receipt uses `none` because the existing contract and behavior remain intact.
