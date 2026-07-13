# Issue #123 - change log

**Branch:** `feature/116-v2-app-shell`
**Mockups:** `journey-4f-course-people.png`, `owner-o7a`, `owner-o7b`, `owner-o7c`

## Summary

- Added the learner instructor, TA, and classmates roster with search/filter/message actions.
- Added the owner roster toolbar, invite copy, enroll modal, Add TA popover, and bulk assignment state.
- Connected cohort invite, enrollment list, and learner enrollment to the production onboarding APIs.
- Kept TA assignment as local UI state because the backend does not expose a TA assignment endpoint.

## Verification

Final verification uses frontend lint, test, build, and multi-viewport browser checks.
