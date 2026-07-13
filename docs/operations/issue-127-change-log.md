# Issue #127 - change log

**Branch:** `feature/116-v2-app-shell`
**Mockups:** `owner-o2-create-course-cohort.png`, `owner-o10a-community-events-owner.png`, `owner-o10b-create-community-event.png`

## Summary

- Added the owner-only Create Course modal to Community Discover, including first cohort and publish settings.
- Connected course/cohort creation to the production onboarding API and inserts the created course into the grid.
- Added owner-only Create Event and Edit actions with one reusable event editor.
- Kept events in local UI state because the backend does not currently expose community-event endpoints.

## Verification

Final verification uses frontend lint, test, build, and multi-viewport browser checks.
