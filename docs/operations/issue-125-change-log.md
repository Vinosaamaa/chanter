# Issue #125 - change log

**Branch:** `feature/116-v2-app-shell`
**Mockups:** `owner-o8-teaching-dashboard.png`, `owner-o9-settings-plan-billing.png`

## Summary

- Added the cross-course Teaching dashboard with support metrics, course queues, AI usage, and shortcuts.
- Connected dashboard metrics and refresh behavior to the production instructor dashboard hook.
- Added the Plan and Billing settings modal, usage meters, invoices, and owner plan upgrade action.
- Linked the sidebar profile control to the settings modal and retained a dimmed app surface behind it.
- Scoped the v2 shell foreground color after browser verification found a collision with the legacy demo `.app-shell` stylesheet that made Teaching text nearly invisible.

## Verification

Final verification uses frontend lint, test, build, and multi-viewport browser checks.
