# Issue #116 CodeAnt Fix Log

PR: #130
Review round: 1 of 3

| Finding | Resolution | Verification |
|---|---|---|
| Critical API mismatch: the routed course workspace used `course-demo` while navigation was loading or the requested course was absent, allowing child hooks to query synthetic course/channel IDs. | Removed the fallback course from routed workspaces. The layout now withholds its outlet during loading and renders explicit error/not-found states. Added regression coverage proving children mount only with the resolved routed course. | `npm run test -- V2CourseWorkspaceLayout.test.tsx`; `npm run lint`; `npm run test`; `npm run build` |

Remaining threads: none after local remediation; awaiting CodeAnt re-review.
