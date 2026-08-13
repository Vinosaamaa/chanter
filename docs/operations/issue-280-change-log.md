# Issue #280 change log — Chanter Engineering adoption

## Outcome

Chanter now owns the released Engineering v1 evidence contracts and a repository-native authoring, validation, correction, historical-publication, and deterministic-projection flow. This change does not deploy Chanter, create an Engineering website, write D1, publish historical backfill, or change product runtime behavior.

## Implementation

- Vendored the three released v1 schemas byte-for-byte.
- Added a non-interactive, public-safe compact receipt scaffold.
- Added exact base/head PR validation with a required PR-body classification.
- Added immutable rich-record validation and exact `id@revision` resolution.
- Added bounded add-only historical batch validation with repository-owner authorization.
- Added deterministic repository-local JSON and portable static HTML projections.
- Added self-teaching instructions to the PR template, agent workflow, and canonical protocol.
- Added an accepted Architecture Review for Chanter’s separate project boundary.

## Verification

Implementation used red-green-refactor cycles for the scaffold, contract hashes, forward validation, historical gating, and projection. Exact commands and hosted CI evidence are recorded on the pull request once its number exists.
