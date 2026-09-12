# Chanter launch execution status

Reviewed 2026-09-11 against Git main `2073de358c7007aba4824ec48e09eafb56196fc5` and the owning GitHub issues.

Chanter has substantial local-beta product code. It has no verified public release. The previous audit's DNS and provider observations are historical; this review does not claim that those external systems have been rechecked.

## Current work

The owner has delegated product and technical decisions, including revisions to the current design. The initial release must use free resources. No provider account or hosting environment exists yet. Paid upgrades and automatic recharge remain disabled. The architecture review may consolidate runtime processes if that produces a more reliable free deployment without weakening authorization or data ownership.

The owner subsequently rejected the current interface and explicitly requested a full modern redesign with mobile and multiple screen sizes. #254 now owns that reconstruction and the reusable, pinned `frontend-design` skill, in addition to its final interaction/accessibility/performance gates. Independent visual work runs alongside launch infrastructure; old visual mockups no longer constrain the new design.

AI provider and model choice must be configurable. OpenAI, Anthropic, xAI/Grok, compatible endpoints and local/no-provider operation belong to #248. Subscription-backed operation must use provider-supported access. ChatGPT API billing is separate from chat subscriptions; xAI's current usage documentation includes API activity in its subscription usage view, so entitlement must be checked per provider and account. Sources: [OpenAI billing](https://help.openai.com/en/articles/9039756), [Claude API access](https://support.claude.com/en/articles/9876003), [xAI usage](https://docs.x.ai/grok/faq).

[#238](https://github.com/Vinosaamaa/chanter/issues/238) through [#241](https://github.com/Vinosaamaa/chanter/issues/241) are closed. [#242](https://github.com/Vinosaamaa/chanter/issues/242), secure browser sessions and transactional email, is in implementation. See its [design](../architecture/secure-browser-sessions-and-email.md) and [implementation record](issue-242-change-log.md).

The open dependency-update PRs and the older environment PR remain separate work. They have not been merged merely because individual test jobs passed. Dependency updates still need exact-head review and Engineering evidence.

## Remaining release work

| Issue | Customer or operator outcome | Current state |
|---|---|---|
| #242 | Reliable sign-in, verification, recovery, device sessions | PR #312 full hosted journeys passed; review fixes need exact-head rerun; provider/HTTPS proof pending |
| #243 | Reproducible staging and production with rollback | Deployment package in progress; free single-host choice recorded, provisioning unverified |
| #244 | Durable private resource storage and quarantine | Open; depends on infrastructure |
| #245 | Durable notifications and search indexing | Open; depends on infrastructure |
| #246 | Truthful supported resource ingestion | Open; depends on storage/events |
| #247 | Authorized production vector retrieval | Open; depends on ingestion |
| #248 | Evaluated AI safety, usage and cost accounting | Provider adapters, model catalog and metering in progress; final retrieval/provider proof pending |
| #249 | Administration, reports and moderation | Open; depends on sessions/infrastructure/events |
| #250 | Truthful free-beta mode or real paid billing | Open; billing mode/provider evidence needed |
| #251 | Export, deletion, retention and accurate policy pages | Open; depends on durable data/moderation |
| #252 | Monitoring, alerts, backups and proven restore | Open; depends on deployed infrastructure |
| #253 | Trusted edge, proxy handling and abuse limits | Open; edge/account evidence needed |
| #254 | Complete modern UI reconstruction, working controls and responsive accessibility | Design and implementation in progress; final capability/browser gates remain |
| #255 | Release-candidate proof and public cutover | Open; requires preceding gates |

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
