# Issue 319 / PR 320: review remediation

## Round 1

Reviewed head: `d7293005dfd208ab6c3161ab8cac4214a0c76845`. Full CodeAnt review completed at 2026-09-12 06:21 UTC. All six functional CI jobs passed, including real signed-in product journeys: [CI run](https://github.com/Vinosaamaa/chanter/actions/runs/34677799471). The complete packaged-library gate also passed: [security run](https://github.com/Vinosaamaa/chanter/actions/runs/34677799443).

| Finding | Correction and verification |
| --- | --- |
| [Malformed current password hashes can throw during login](https://github.com/Vinosaamaa/chanter/pull/320#discussion_r3995383942) | A new login regression first failed with IllegalArgumentException instead of the neutral 401. Reject malformed encoding, perform normal current-format dummy work, and retain the existing BCrypt verification work and account-lock boundary. |
| [Repeated scans retain extracted packages and raw reports](https://github.com/Vinosaamaa/chanter/pull/320#discussion_r3995383947) | Remove the active run directory in finally after either success or failure. Cleanup verifies the resolved directory lies immediately inside the scan cache and has the generated run prefix; it preserves the sanitized summary and adjacent files. A focused cleanup-boundary regression and an actual all-service scan verify the change. |

The affected auth/common Maven verification and all four scanner regression cases passed. The real all-service package scan again found zero high/critical vulnerabilities or secrets across 749 libraries, and its run-directory count was unchanged after completion, proving cleanup ran. Its full hosted CI, package scan and completed CodeAnt review remain separate merge requirements. No advisory suppression, production credentials or deployment change is included in this round.
