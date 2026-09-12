# Issue #311 CodeAnt review

**Pull request:** [#313](https://github.com/Vinosaamaa/chanter/pull/313)

| Pass | Finding | Resolution | Verification |
| --- | --- | --- | --- |
| Initial review of `30a6840` | An exit-code-only assertion could accept an unrelated scaffold error. | Added an assertion for the exact public-safety error category while retaining the no-echo and no-file checks. | `node --test scripts/tests/engineering-*.test.mjs` |
| Initial review of `30a6840` | Extract two repeated commands into a composite action. | Deferred. The jobs share two one-line commands; a new action would add an indirection without removing a concrete source of failures. Keeping the existing Ubuntu policy job intact also preserves its established check name and PR metadata validation. | Both hosted Engineering jobs passed at `30a6840`; future runs continue to execute the same test glob and projection command. |

Review source: [CodeAnt suggestions](https://github.com/Vinosaamaa/chanter/pull/313#issuecomment-5643417609). No inline review threads were present at this pass. Broader CI failures from the unavailable MinIO image and frontend dependency audit remain blocking and are coordinated separately; this PR does not bypass those checks.
