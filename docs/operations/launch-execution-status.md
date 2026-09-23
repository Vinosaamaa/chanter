# Chanter launch execution status

Reviewed 2026-09-22 against accepted main `634b9dd1ee7880bf6f4d6a7a617bd7f4e24530ac`, linked PRs and the owning GitHub issues. PR336 passed its exact-head gates and merged-main application, security and native-boundary checks. In-progress branch changes are identified separately below.

Chanter has substantial local-beta product code. It has no verified public release. The previous audit's DNS and provider observations are historical; this review does not claim that those external systems have been rechecked.

## Current work

The owner has delegated product and technical decisions, including revisions to the current design. The initial release must use free resources. No provider account or hosting environment exists yet. Paid upgrades and automatic recharge remain disabled. The architecture review may consolidate runtime processes if that produces a more reliable free deployment without weakening authorization or data ownership.

The owner subsequently rejected the current interface and explicitly requested a full modern redesign with mobile and multiple screen sizes. #254 now owns that reconstruction and the reusable, pinned `frontend-design` skill, in addition to its final interaction/accessibility/performance gates. Independent visual work runs alongside launch infrastructure; old visual mockups no longer constrain the new design.

AI provider and model choice must be configurable. OpenAI, Anthropic, xAI/Grok, compatible endpoints and local/no-provider operation belong to #248. Subscription-backed operation must use provider-supported access. ChatGPT API billing is separate from chat subscriptions; xAI's current usage documentation includes API activity in its subscription usage view, so entitlement must be checked per provider and account. Sources: [OpenAI billing](https://help.openai.com/en/articles/9039756), [Claude API access](https://support.claude.com/en/articles/9876003), [xAI usage](https://docs.x.ai/grok/faq).

[#238](https://github.com/Vinosaamaa/chanter/issues/238) through [#241](https://github.com/Vinosaamaa/chanter/issues/241) are closed. Secure browser sessions and transactional email are merged in PR312; actual inbox/provider and public HTTPS verification keep #242 open. See its [design](../architecture/secure-browser-sessions-and-email.md) and [implementation record](issue-242-change-log.md).

The free deployment package now builds, scans, stages and uploads immutable AMD64
and ARM64 bundles. Actual publication succeeded at main `7d42dd7` after PR326,
with matching checksum assets. This is a draft release of that commit, not a
public server or the final launch candidate. Accepted semantic retrieval now
requires epoch8, accepted moderation requires epoch9, and pending account lifecycle
requires epoch10. Oracle Free Tier account
signup is the owner's pending identity-verification step; no credentials or
provider account are fabricated, and paid provisioning remains disabled.

The accepted UI reconstruction is in PR314 and answer controls in PR322.
Document ingestion PR325, native subscription support PR324 and public-edge
controls PR328 are merged with passing merged-main checks. All workers are configured as Astra High
without a fast-mode override. Their issue branches and worktrees are preserved.
PR329's private telemetry and database/configuration recovery foundation is also
merged, with passing full application and native release checks plus merged-main
verification. PR334 monitoring is now merged, with both native staging checks and
merged-main CI passing. Actual monitoring-provider acceptance remains under #331.

PR338 dependency repairs and PR341 owner cohort-invite authorization are merged with passing full review and merged-main checks. The dependency repair passed both native release architectures, and all eight security alerts are fixed. The final combined release remains a separate gate. Existing branches and worktrees are preserved.

PR336 recovery infrastructure is merged after both native release architectures,
application checks and full review. Its public capability remains disabled.
#342, a child of #332, owns actual source replay, private-object inventory and
restore, original-writer closure and the remaining provider guarantees. A worker
startup fixture is not proof of complete application recovery.

The current #251 source checkpoint is `21830727`, with independently reviewed
cleanup and retained-record boundaries. #342 now includes encrypted object and
inventory-manifest checks, ordinary S3 deletion safeguards and actual PostgreSQL
pagination proof. Its earlier full recovery rehearsal failed before database
restore because the packaged helper sent the wrong private-auth header. That
header is corrected at `7605ae91`; renewed restore proof and the disabled production
byte adapter remain outstanding. Neither partial proof authorizes cutover.

#339 preserves deletion receipts after expired confirmation sessions and invitation
intent through verification/reload. Responsive enrollment management is being
verified alongside the existing reconstructed pages. #344 / PR345 extracts the
independent audio-evidence and Teaching corrections needed for source branches to
pass their existing CI, without importing unfinished account UI or raising budgets.
The final source/UI/recovery merge and release candidate have not been accepted.

## Remaining release work

| Issue | Customer or operator outcome | Current state |
|---|---|---|
| #242 | Reliable sign-in, verification, recovery, device sessions | PR312 merged and main journeys passed; actual SMTP/public HTTPS proof remains |
| #243 | Reproducible staging and production with rollback | PR315 and repair326 merged; real dual-architecture package upload passed; account/provisioning/public deployment remain |
| #244 | Durable private resource storage and quarantine | PR318 merged; native scanning passed; real private bucket and off-host restore proof remain |
| #245 | Durable notifications and search indexing | PR327 merged; main CI, real PostgreSQL and consumer-restart journeys passed; production delivery proof remains |
| #246 | Truthful supported resource ingestion | PR325 merged; real supported parsers and durable ingestion validated; provider/final release proof remains |
| #247 | Authorized production vector retrieval | PR330 merged with full checks, dual-architecture packaged release and 100,000-chunk proof with private telemetry within 640 MiB; merged-main checks passed; real production proof remains |
| #248 | Evaluated AI safety, usage and cost accounting | PR317/322 merged provider adapters, catalog, accounting and answer controls; final retrieval/evaluations and configured-provider proof remain |
| #249 | Administration, reports and moderation | PR333 merged with full CI/review, both native stages, real audio revocation/reconnect denial, emailed appeal/reversal and phone/desktop proof; merged-main checks passed; production operator enrollment remains |
| #250 | Truthful free-beta mode or real paid billing | PR323 merged truthful free-beta mode and limits; final deployed accounting proof remains; paid billing is outside initial free beta |
| #251 | Export, deletion, retention and accurate policy pages | Draft335 implements bounded exports, terminal fences, seven private source handlers, late-answer retraction and retained-record cleanup; final source/recovery union, deployed proof and operator/legal details remain |
| #252 | Monitoring, alerts, backups and proven restore | PR329 foundation, PR334 monitoring and PR336 recovery infrastructure merged; #342 owns real source/object restore and original-writer closure. Combined release and actual provider/operator receipts remain gates |
| #253 | Trusted edge, proxy handling and abuse limits | PR328 merged with shared admission and request bounds; actual public proxy/provider proof remains |
| #254 | Modern responsive UI and complete interactions | PR314 reconstruction and PR322 answer UI merged; #339 owns remaining integrated journeys, control review and responsive interaction fixes; final whole-product/mobile/voice checks remain |
| #255 | Release-candidate proof and public cutover | Not launched; requires implementation gates plus actual accounts, public services, recovery and release proof |
| #337 | Patched edge and frontend test dependencies | PR338 merged with full review, actual compatibility regressions and both native release architectures passing; no open dependency security alerts at acceptance |
| #344 | Independent source-branch CI prerequisite | Draft345 extracts retained audio statistics and Teaching consolidation under unchanged budgets; exact-head checks and review remain required |

Native subscription support is tracked separately in #316. Merged PR324 has
verified Windows/Linux packaging, exact-origin pairing, isolated provider execution
and durable saved-answer status delivery. Its accepted release uses schema epoch7.
No actual provider login or subscription inference has been performed. API-based
provider choice remains independent of this optional desktop capability.

The #252 implementation is split at the dependency boundary. PR329's database,
configuration and telemetry foundation enables #247. Complete application restore
then consumes #251's current deletion journal in #332. #331 completes business
metrics, error tracking and actual alert delivery. This avoids making retrieval
wait for deletion work that itself follows retrieval. Parent #252 stays open;
neither foundation acceptance nor a database-only restore authorizes public launch.

The [ordered breakdown](../issues/product-readiness-issue-breakdown.md) defines dependencies and acceptance. This table summarizes that program; it does not create a second issue queue.

## How to understand a completed slice

1. Start with the issue's acceptance criteria and linked PR.
2. Read `docs/operations/issue-<number>-change-log.md` for the implementation, verification and remaining constraints.
3. Follow its design and system-review links for the state model and tradeoffs.
4. Read `docs/engineering/changes/pr-<number>.md` for the compact receipt, then the exact linked rich-record revision.
5. Check the PR's tested head, review findings, merge result and required provider/release receipts.

## Use the repository's Engineering workflow

The reusable workflow is checked in; no missing editor plugin is needed to run it. From the repository root:

```sh
node scripts/new-engineering-receipt.mjs --help
node --test scripts/tests/engineering-*.test.mjs
node scripts/build-engineering-journal.mjs --check
node scripts/build-engineering-journal.mjs
```

The final command produces `.engineering-journal/generated/portable.html`, a local browsable index of canonical Engineering records. It is a generated reference artifact, not the Chanter product or a deployed website. The complete authoring contract is [here](../engineering/pull-request-history.md).

## Definition of launch

Launch requires a real configured environment and a tested release serving the public hostname. It also requires customer email delivery, role-aware browser journeys, resource persistence, AI/provider behavior, billing-mode truth, support/moderation, export/deletion, restore and rollback receipts. The final release issue remains open until those outcomes are observed.
