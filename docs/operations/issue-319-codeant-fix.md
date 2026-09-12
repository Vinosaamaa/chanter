# Issue 319 / PR 320: review remediation

## Round 1

Reviewed head: `d7293005dfd208ab6c3161ab8cac4214a0c76845`. Full CodeAnt review completed at 2026-09-12 06:21 UTC. All six functional CI jobs passed, including real signed-in product journeys: [CI run](https://github.com/Vinosaamaa/chanter/actions/runs/34677799471). The complete packaged-library gate also passed: [security run](https://github.com/Vinosaamaa/chanter/actions/runs/34677799443).

| Finding | Correction and verification |
| --- | --- |
| [Malformed current password hashes can throw during login](https://github.com/Vinosaamaa/chanter/pull/320#discussion_r3995383942) | A new login regression first failed with IllegalArgumentException instead of the neutral 401. Reject malformed encoding, perform normal current-format dummy work, and retain the existing BCrypt verification work and account-lock boundary. |
| [Repeated scans retain extracted packages and raw reports](https://github.com/Vinosaamaa/chanter/pull/320#discussion_r3995383947) | Remove the active run directory in finally after either success or failure. Cleanup verifies the resolved directory lies immediately inside the scan cache and has the generated run prefix; it preserves the sanitized summary and adjacent files. A focused cleanup-boundary regression and an actual all-service scan verify the change. |

The affected auth/common Maven verification and all four scanner regression cases passed. The real all-service package scan again found zero high/critical vulnerabilities or secrets across 749 libraries, and its run-directory count was unchanged after completion, proving cleanup ran. Its full hosted CI, package scan and completed CodeAnt review remain separate merge requirements. No advisory suppression, production credentials or deployment change is included in this round.

## Round 2

Reviewed head: `f282801a9d0aeb8d8dd7e826ec0d6f01618447c7`; full review completed at 2026-09-12 06:27 UTC. All six hosted CI jobs, including real signed-in journeys, subsequently passed in [CI run](https://github.com/Vinosaamaa/chanter/actions/runs/34678079749); the hosted package scan passed in [security run](https://github.com/Vinosaamaa/chanter/actions/runs/34678079757).

- [Library path coverage](https://github.com/Vinosaamaa/chanter/pull/320#discussion_r3995399807): confirmed that a same-named JAR elsewhere in the package could satisfy a basename-only coverage check. Extended the existing regression first: both a classes directory and a nested alternate BOOT-INF directory incorrectly passed. Coverage now accepts only the exact root-relative `BOOT-INF/lib/<library>.jar` paths emitted by Trivy. The real scan must still identify every packaged library under that stricter check.
- The prior raw-directory retention comment remained attached after remediation. The finally cleanup, verified directory boundary, passing cleanup regression and unchanged run-directory count from the real scan establish that new runs are removed. Historical ignored evidence predating the fix is not automatically deleted; no additional source change is needed for this repeated finding.

All four scanner regressions passed after the path correction. An actual scan with the exact root-relative path check again identified all 749 packaged libraries and reported zero high/critical vulnerabilities or secrets across all ten services. No application runtime code changed in this round.
