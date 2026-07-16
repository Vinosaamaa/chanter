# Issue #193 Change Log — SEC-13: CI least-privilege GITHUB_TOKEN permissions

## Problem

`.github/workflows/ci.yml` had no explicit `permissions:` block. The workflow token therefore inherited the repository default (often broader than needed, e.g. write `contents`). CI jobs build untrusted PR code, so an exploited build step would run with more token scope than the workflow requires.

## Changes

### `.github/workflows/ci.yml`

- Added top-level:

```yaml
permissions:
  contents: read
```

- Both `backend` and `frontend` jobs only checkout, build, and test; neither needs write access, packages, or pull-request mutation.

## Acceptance

- [x] CI workflow has least-privilege permissions (`contents: read` only)
- [ ] CI still passes on PR (verified after merge gate)

## Notes

No application runtime change. Browser auth validation after merge confirms the product stack is unaffected.
