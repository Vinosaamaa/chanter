# Issue #241 debug log: Trustworthy release gates

**Date:** 2026-08-09  
**Scope:** test isolation, Java selection, browser personas, dependency remediation, and CI diagnostics.

## 1. Product script tests were contaminated by `.env`

### Symptom

`make product-test` reported that the missing-`REDIS_PASSWORD` case exited `0` and emitted no validation message.

### Root cause

Make globally included and exported `.env`. The test sourced an environment file that intentionally omitted Redis, but the already-exported parent value survived and invalidated the test setup.

### Fix

Run test commands through a process boundary that unsets Chanter, Spring, service, database, provider, demo, and Vite runtime variables. A regression proves runtime configuration is absent while ordinary test-runner state is preserved.

## 2. Java selection was implicit and allowed Java 23

### Symptom

The interactive shell used Java 17, Make used macOS-only parse-time discovery, non-macOS commands had no repository validation, and the product helper accepted Java 23.

### Fix

Added one executable Java 21 boundary used by Make and product startup, plus Maven Enforcer for direct `mvn` calls. Java 17 now fails at `validate` with the repository-specific remediation message.

## 3. Root-level Maven invocation missed backend settings

### Symptom

The first Make rewrite used `mvn -f backend/pom.xml`, which failed because `backend/.mvn/maven.config` referenced `.mvn/settings.xml` relative to the invocation directory.

### Root cause

Maven discovers `.mvn` configuration from its invocation directory, not from the POM passed with `-f`.

### Fix

Repository-root Make targets pass `-s backend/.mvn/settings.xml -f backend/pom.xml` explicitly. Product scripts still execute Maven from `backend/`, where the native configuration works.

## 4. Generic Member was absent from the product seed

### Symptom

The new membership-only Playwright journey timed out on `/sign-in`.

### Root cause

Only Owner and enrolled Learner accounts existed. CI therefore could not exercise the generic membership authorization fixed in #239.

### Fix

Added an idempotent Member registration, invitation lookup/create, and acceptance flow without Cohort enrollment. The resulting browser test verifies server navigation and explicitly rejects Course visibility.

## 5. Node release test collided with Vitest discovery

### Symptom

The bundle test passed under `node --test`, but `npm test` discovered `check-bundle-budget.test.mjs` and reported that it contained no Vitest suite.

### Fix

Renamed the file to `check-bundle-budget.node-test.mjs` and kept it behind `npm run test:release`, outside Vitest's `*.test.*` pattern.

## 6. Backend test failure inside the agent sandbox

### Symptom

Gateway tests failed to bind an ephemeral Netty port with `SocketException: Operation not permitted`.

### Conclusion

This was host-network sandbox isolation, not an application failure. The same hermetic command passed outside that restriction. CI uses a normal GitHub runner and retains Surefire reports when a real failure occurs.

## 7. Current npm High advisories

The initial production audit found two High React Router advisories. The full graph found eight High advisories across React Router, Playwright, PostCSS, Nano ID, brace expansion, and Undici. `npm audit fix` updated nine locked packages within declared compatible ranges; both production and full audits then reported zero vulnerabilities.

## 8. GitHub dependency review could not read a dependency graph

### Symptom

The new dependency-review job failed immediately with `Dependency review is not supported on this repository`.

### Root cause

The public repository's dependency graph was disabled at the GitHub repository level. Workflow configuration alone cannot make dependency review available.

### Fix

Enabled repository vulnerability alerts, which enabled the dependency graph, and confirmed GitHub can export the repository SBOM. The failed job is rerun after that repository setting is active.

## 9. Frontend-only browser CI contacted the absent backend

### Symptom

The public Playwright suite passed against the locally running product but failed in frontend CI with `502 GET /api/v1/auth/oauth/providers`.

### Root cause

That job deliberately serves only Vite. The sign-in page performs an optional OAuth-provider discovery request, so the stricter browser-health fixture correctly reported the missing backend.

### Fix

The frontend-only public suite returns an empty provider catalog for that single optional request. The signed-in full-stack suite still calls the real gateway and rejects request, response, console, and page failures.
