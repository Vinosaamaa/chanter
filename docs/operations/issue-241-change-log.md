# Issue #241 change log: Trustworthy release gates

**Issue:** [#241](https://github.com/Vinosaamaa/chanter/issues/241)  
**Branch:** `chore/241-trustworthy-release-gates`  
**Date:** 2026-08-09

## Outcome

Changed the release signal from public-page smoke tests into deterministic Java, dependency, bundle, and signed-in product gates. CI now starts the full product stack with fresh generated secrets, seeds distinct Owner, membership-only Member, and enrolled Learner personas, and runs authorization-aware browser journeys. Failures retain reports, screenshots, videos, traces, JUnit output, backend Surefire reports, and product service logs for 14 days.

## TDD evidence

### Hermetic test process

`make product-test` initially failed because Make exported the local `.env`; the missing-Redis test inherited `REDIS_PASSWORD` and returned success instead of the expected validation failure. A command-level regression now proves Chanter/Spring/infra variables are removed while unrelated tooling variables remain available. Both `backend-test` and `product-test` run through that boundary.

### Java 21 boundary

The repository shell default was Java 17 and direct Maven accepted it. A failing runner test first proved Java 17 lacked a useful rejection. `scripts/java21.sh` now selects JDK 21 on macOS, validates it on every platform, and fails before the requested command on incompatible runtimes. Maven Enforcer independently requires `[21,22)` at `validate`, including direct Maven use.

### Bundle budget

A Node test first created a 101-byte JavaScript asset against a 100-byte limit and failed because no checker existed. Production builds now enforce raw and gzip budgets for JavaScript and CSS. The current build is below all limits:

```text
JavaScript  1203.3 KiB raw / 321.6 KiB gzip  (budgets: 1269.5 / 341.8 KiB)
CSS          194.0 KiB raw /  34.9 KiB gzip  (budgets:  214.8 /  43.9 KiB)
```

### Membership-only browser persona

The new generic-member test initially remained on `/sign-in` because the product seed only created Owner and Learner accounts. The seed now registers `dev-demo-member@chanter.local`, accepts a Study Server invitation idempotently, and deliberately does not enroll that user in a Cohort. The browser test proves navigation returns `200`, exposes `general`, and returns an empty Course scope.

## Code and configuration changes

- Added `scripts/testing/run-hermetic.sh` and regression coverage; wired Make test and verify targets through it.
- Added the Java 21 selector/validator and Maven Enforcer; removed the Java 23 fallback.
- Preserved Maven `verify` as the CI lifecycle through `make backend-verify`.
- Remediated all current npm advisories through lockfile updates, including patched React Router and Playwright.
- Added an enforced production bundle budget and its Node-native test.
- Added a reusable Playwright release fixture that rejects console errors, page exceptions, failed requests, and first-party HTTP failures.
- Replaced route-presence checks with settled page content, role authorization, and horizontal-overflow assertions at 720p, 1080p, mobile, and 4K.
- Added npm, Maven, and GitHub Actions coverage to Dependabot alongside existing Docker coverage.
- Added pull-request dependency review, production/full npm audits, deterministic `npm ci`, signed-in product E2E, and retained diagnostics to CI.
- Enabled the repository dependency graph so GitHub can execute dependency review and publish its SBOM.
- Isolated the optional OAuth-provider lookup in the frontend-only public suite; the full-stack suite continues to exercise the real endpoint.

## Local verification

```text
make backend-test                              PASS (Java 21, hermetic full suite)
make backend-verify                            PASS (Java 21, Maven verify lifecycle)
make product-test                              PASS
npm run lint                                   PASS
npm test                                       PASS (67 files / 217 tests)
npm run test:release                           PASS
npm run build                                  PASS + bundle budget
npm audit --omit=dev --audit-level=high        PASS (0 vulnerabilities)
npm audit --audit-level=high                   PASS (0 vulnerabilities)
Docker Compose product profile validation      PASS
Public critical browser suite                  PASS (7/7)
Signed-in Owner/Member/Learner browser suite   PASS (11/11)
make product-health                            PASS (gateway, auth, realtime, LiveKit)
```

The existing Vite large-chunk and ineffective-dynamic-import warnings remain visible and bounded by the new budget. Deeper code splitting and route-wide performance thresholds remain owned by #254.

## Architecture and operations

No service boundary, persistence model, or public API changed. This issue changes release engineering and demo data only, so no System Design or architecture decision record update is required. The next launch slice is [#242](https://github.com/Vinosaamaa/chanter/issues/242), transactional email and durable secure browser sessions.
