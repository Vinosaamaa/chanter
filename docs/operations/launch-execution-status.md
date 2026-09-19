# Chanter launch execution status

Reviewed 2026-09-19 against accepted main `935f6f849ae72c3682ed26d033aa543a9be0b4d8`, linked PRs and the owning GitHub issues. In-progress branch changes are identified separately below.

Chanter has substantial local-beta product code. It has no verified public release. The previous audit's DNS and provider observations are historical; this review does not claim that those external systems have been rechecked.

## Current work

The owner has delegated product and technical decisions, including revisions to the current design. The initial release must use free resources. No provider account or hosting environment exists yet. Paid upgrades and automatic recharge remain disabled. The architecture review may consolidate runtime processes if that produces a more reliable free deployment without weakening authorization or data ownership.

The owner subsequently rejected the current interface and explicitly requested a full modern redesign with mobile and multiple screen sizes. #254 now owns that reconstruction and the reusable, pinned `frontend-design` skill, in addition to its final interaction/accessibility/performance gates. Independent visual work runs alongside launch infrastructure; old visual mockups no longer constrain the new design.

AI provider and model choice must be configurable. OpenAI, Anthropic, xAI/Grok, compatible endpoints and local/no-provider operation belong to #248. Subscription-backed operation must use provider-supported access. ChatGPT API billing is separate from chat subscriptions; xAI's current usage documentation includes API activity in its subscription usage view, so entitlement must be checked per provider and account. Sources: [OpenAI billing](https://help.openai.com/en/articles/9039756), [Claude API access](https://support.claude.com/en/articles/9876003), [xAI usage](https://docs.x.ai/grok/faq).

[#238](https://github.com/Vinosaamaa/chanter/issues/238) through [#241](https://github.com/Vinosaamaa/chanter/issues/241) are closed. Secure browser sessions and transactional email are merged in PR312; actual inbox/provider and public HTTPS verification keep #242 open. See its [design](../architecture/secure-browser-sessions-and-email.md) and [implementation record](issue-242-change-log.md).

The free deployment package now builds, scans, stages and uploads immutable AMD64
and ARM64 bundles. Actual publication succeeded at main `7d42dd7` after PR326,
with matching checksum assets. This is a draft release of that commit, not a
public server or the final launch candidate. Subsequent accepted durable events
require the reviewed epoch5 package before deployment. Oracle Free Tier account
signup is the owner's pending identity-verification step; no credentials or
provider account are fabricated, and paid provisioning remains disabled.

The accepted UI reconstruction is in PR314 and answer controls in PR322.
Document ingestion PR325, native subscription support PR324 and public-edge
controls PR328 are merged with passing merged-main checks. All workers are configured as Astra High
without a fast-mode override. Their issue branches and worktrees are preserved.

The open dependency-update PRs and the older environment PR remain separate work. They have not been merged merely because individual test jobs passed. Dependency updates still need exact-head review and Engineering evidence.

## Remaining release work

| Issue | Customer or operator outcome | Current state |
|---|---|---|
| #242 | Reliable sign-in, verification, recovery, device sessions | PR312 merged and main journeys passed; actual SMTP/public HTTPS proof remains |
| #243 | Reproducible staging and production with rollback | PR315 and repair326 merged; real dual-architecture package upload passed; account/provisioning/public deployment remain |
| #244 | Durable private resource storage and quarantine | PR318 merged; native scanning passed; real private bucket and off-host restore proof remain |
| #245 | Durable notifications and search indexing | PR327 merged; main CI, real PostgreSQL and consumer-restart journeys passed; production delivery proof remains |
| #246 | Truthful supported resource ingestion | PR325 merged; real supported parsers and durable ingestion validated; provider/final release proof remains |
| #247 | Authorized production vector retrieval | Draft330 implements pinned ONNX embeddings and scoped pgvector; dual-architecture load tests pass at 100,000 chunks; final concurrency, runtime union and release proof remain |
| #248 | Evaluated AI safety, usage and cost accounting | PR317/322 merged provider adapters, catalog, accounting and answer controls; final retrieval/evaluations and configured-provider proof remain |
| #249 | Administration, reports and moderation | Active branch implements step-up operator access, reports/appeals and live restrictions; complete database-driven media and responsive UI proof remains |
| #250 | Truthful free-beta mode or real paid billing | PR323 merged truthful free-beta mode and limits; final deployed accounting proof remains; paid billing is outside initial free beta |
| #251 | Export, deletion, retention and accurate policy pages | Active branch has bounded source exports, durable transport and terminal journal/checkpoint tests; public export/deletion and restore reapply are still underway |
| #252 | Monitoring, alerts, backups and proven restore | Draft329 supplies private telemetry and encrypted database/configuration recovery; #331 owns remaining operational alerts and #332 complete application recovery; all remain launch gates |
| #253 | Trusted edge, proxy handling and abuse limits | PR328 merged with shared admission and request bounds; actual public proxy/provider proof remains |
| #254 | Modern responsive UI and complete interactions | PR314 reconstruction and PR322 answer UI merged; new capability integration and final whole-product/mobile/voice checks remain |
| #255 | Release-candidate proof and public cutover | Not launched; requires implementation gates plus actual accounts, public services, recovery and release proof |

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
