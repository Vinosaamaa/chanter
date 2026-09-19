# Issue 250 system review

The community service owns effective Study Server entitlements. Its plan read now replaces any historical tier's quota with validated process configuration. The old authenticated PATCH route rejects all changes; the repository no longer exposes a plan writer. Legacy tier rows remain untouched, so the migration does not fabricate a transaction or erase history.

`CHANTER_BETA_MODE` defaults to `free_beta`; every other value fails startup. `CHANTER_BETA_ASSISTANT_RUN_LIMIT` defaults to 1,000 and must be within 1..1,000. All Study Servers in a deployment receive the same operator limit. Configuration rollout and production wiring belong to #243. No browser value can increase the entitlement. Backend plan consumers retain existing fields and receive explicit operator/lifetime metadata.

The assistant service continues enforcing the limit against its existing lifetime audit count. This change does not repair concurrent count-then-invoke admission or combine token and object-storage meters. Provider token reservations and object limits remain separate authorities in #248 and #244. Saved runs are counted; there is no monthly reset. Exhaustion points users to existing Course resources and instructor support.

The Usage page derives access from the owned-server list and selected server's verified owner response. Unrelated server navigation requests cannot block usage. It does not display usage until the selected server's dashboard request finishes successfully. Loading and transport failures cannot become a fake zero; access-loading failures remain visible instead of masquerading as lost permission. Old billing links redirect to Usage. The dashboard and development harness contain no plan-changing controls.

Tests cover operator bounds, unsupported mode rejection, owner and stranger PATCH rejection, unchanged historical data and legacy high-tier override. Browser fixtures cover presentation and failure states; the separate real-service journey verifies persisted policy and rendered usage. Final exact-head CI/full review, merged-main and deployment proof remain required. Paid-provider acceptance remains unimplemented and #250 stays open.
