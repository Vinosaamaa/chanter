# Issue #145 — Truthful owner billing and usage settings

## Summary

Rewrote Plan & Billing settings to use real Study Server SaaS plan and AI usage from the instructor dashboard. Removed fabricated invoices, storage meters, prices, and billing dates.

## Changes

- Owner gate via `canManageBilling` / `showBillingNav` (non-owners redirect to Home)
- Real server name, plan tier labels, AI used/limit/remaining
- Quota warning only when exhausted or ≥80% used
- Plan change uses existing `updateSaasPlan` / `savePlan` with explicit local-SaaS (no payment) copy
- Unsupported General/Members/Integrations controls disabled with reasons
- Storage, invoice history, compare plans, download, and fake fallbacks removed

## Tests

- `BillingSettingsPage.test.tsx` — non-owner redirect; real plan/usage; no invoice/storage/$29
