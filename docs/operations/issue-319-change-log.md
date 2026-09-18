# Issue 319: supported backend dependencies

Tracking: [issue 319](https://github.com/Vinosaamaa/chanter/issues/319), [PR 320](https://github.com/Vinosaamaa/chanter/pull/320). This is the independent security prerequisite found by the first native release-package scan for issue 243. It does not close public launch issue 255.

## Implementation

- Spring Boot 4.0.8, Spring Cloud 2025.1.3 and Java 21; fixed Tomcat 11.0.25, Nimbus 10.9.1 and test-only H2 2.5.250.
- Official Jackson 2 compatibility, new Gateway route namespace, dedicated Flyway starter, relocated health and test annotations, and specific MVC/WebFlux test starters.
- Versioned Spring Security PBKDF2 for new full-length passwords, retained legacy BCrypt verification and a current-work-factor dummy hash for absent accounts.
- Actual packaged-library scanning with completeness enforcement; readonly hosted workflow downloads a checksum-pinned Trivy binary and retains only sanitized summaries.

Design and system review: `docs/architecture/supported-backend-and-security-scans.md`; rich record: `architecture-review-supported-backend-runtime@1`.

## Reproductions and local verification

1. The original native application image reported 33 high/critical findings. No advisory suppression was added.
2. The first Boot 4 compile identified moved framework health/test types. The service suite then reproduced missing WebTestClient auto-configuration, a Jackson 3/Jackson 2 manual-client mismatch, and H2's closed-session CHECK constraint defect. Focused fixes retain existing assertions.
3. `AuthPasswordEncodingTest` reproduced BCrypt rejecting the existing 128-character and multibyte password contract. The fix verifies the complete suffix and still accepts existing unprefixed BCrypt hashes.
4. `node --test scripts/security/backend-artifacts.test.mjs`: three passing cases. Metadata-only or incomplete scans fail; complete scans still fail on vulnerabilities or secrets; complete clean scans pass.
5. A real Trivy filesystem scan proved that source metadata alone can omit packaged Java dependencies. Rootfs scanning then identified all 95 gateway libraries and all packaged libraries in the other nine services. The remaining three Tomcat advisories in each MVC service justified the explicit 11.0.25 security override.
6. Final `mvn -B -q -s backend/.mvn/settings.xml -f backend/pom.xml verify` passed after the Tomcat and password fixes: 313 tests, zero failures and zero errors.
7. Final `node scripts/security/backend-artifacts.mjs <verified-trivy-executable>` passed for all ten application packages: 749 packaged libraries across the services, complete coverage, zero high/critical vulnerabilities and zero secret findings. Per-service library counts are gateway 95, auth 75, community 91, message 68, realtime 92, media 68, agent 68, analytics 56, search 68 and notification 68. These are per-package counts, not a claim of 749 unique dependencies.
8. Independent review measured a substantial legacy BCrypt versus missing-account PBKDF2 timing difference. Two new regressions first failed: failed logins must run both work factors for all account formats, and overlong legacy guesses must still perform bounded BCrypt work without authenticating a truncated input. The correction uses both work factors in fixed order and retains the complete PBKDF2 input. The affected auth/common verification and refreshed ten-service package scan both passed.
9. All 34 Engineering policy, authoring and projection tests passed after refreshing the unchanged released schema files to the canonical LF bytes supplied by merged PR 313. No schema content change is part of this repair.
10. Rebased onto the merged AI provider foundation from PR 317. Preserved its exact assistant-models gateway predicate under the new WebFlux route namespace and updated its new MVC test annotation. Full integrated backend verification passed: 360 tests, zero failures/errors. The refreshed ten-service scan again covered all 749 packaged libraries with zero high/critical vulnerabilities or secret findings. Fresh exact-head hosted checks and real-product journeys remain required.

## Required hosted and release evidence

Exact-head full CI, real product journeys, complete package scan and full CodeAnt review remain pending. Merged-main checks and both native release architectures in issue 243 remain required after merge. The production compatibility epoch must advance for the new password decoder; the subsequent media migration advances it again. Existing user data and branch/worktree state are preserved.
