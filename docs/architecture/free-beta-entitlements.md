# Free beta entitlements

Issue #250 owns the removal of simulated billing. The launch decision is a free beta: no checkout, card collection, invoices, trial state, price or promise of a paid subscription. Paid-provider behavior remains explicitly unavailable and requires the remaining #250 provider acceptance criteria before it can be enabled.

## Current defect

The owner-facing plan selector sends PATCH to a service that changes its own AI quota from 5 to 100 or 1,000. No operator or payment event authorizes that entitlement. The interface calls the count monthly, but the repository counts the complete audit history and implements no monthly reset. Historical tier rows therefore cannot serve as commercial evidence.

## Backend implementation

Remove the tier mutation from the repository and service. Keep the former PATCH route as an authenticated, explicit rejection so old clients cannot silently mutate entitlements. Plan reads derive their effective free-beta entitlement from operator configuration, independent of the legacy plan_tier column. Preserve historical rows; do not rewrite them to suggest a transaction occurred.

The only accepted launch mode is free_beta. Unsupported paid mode fails startup. The beta assistant-run limit defaults to 1,000 per Study Server and is bounded between 1 and 1,000 by deployment configuration. The limit applies to the existing lifetime audit count and is described that way. It is distinct from the provider token reservation limits in #248 and private-object byte/request limits in #244. This slice cannot raise either provider budget.

Read responses identify FREE_BETA, OPERATOR_POLICY and LIFETIME explicitly. Existing response fields remain available for service consumers; effective planTier becomes FREE_BETA. No browser-supplied amount, tier or quota is accepted. Exhaustion directs users to instructors and source material; it offers neither a paid upgrade nor a fictitious reset date.

## UI design

Retain the merged learning-desk design: Instrument Sans, ink #192C46, muted text #596A80, blue #2458D3, white surfaces and light borders. Replace the billing destination with a simple owner usage page. The old billing deep link redirects there. The page has one free-beta explanation, a server selector when needed, and the actual assistant-run count and remaining limit. No tier comparison, pricing grid, card icon or disabled checkout is shown.

The existing instructor dashboard and development harness lose their plan mutation controls. Keep direct routes back to Home, keyboard-visible focus, readable narrow-screen layout and actual loading/error/empty states. Product and fixture browser checks must cover the new usage destination at phone and desktop widths.

## System review and verification

Direct owner API tampering must return a rejection without changing the effective limit or stored legacy tier. Operator configuration must determine the read limit; an old high tier must not override it. Invalid mode and out-of-bound limits must fail startup. Existing server membership and provider-generation authorization remain intact.

Verify the backend through public HTTP requests and stored state, frontend through visible controls and navigation, and complete product journeys through the real services. Update the exact Engineering receipt, design/change log and full review before merge. Keep #250 open for any remaining provider or consolidated quota acceptance; do not mark paid mode implemented by documenting it.
