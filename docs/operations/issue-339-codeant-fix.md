# Issue #339 CodeAnt review dispositions

## First full review

The full review completed for 50fc412313acc4a12635bf64cd97502dd864e933. The earlier conversational answer and static quality gate were not counted as the full review. Hosted verification and the final lifecycle/recovery union still gate acceptance.

- 4077204363, browser-health ownership: fixed. A request arriving after navigation begins has ambiguous document ownership and cannot inherit the old document's cancellation allowance. Redirects retain the original snapshot rather than adopting requests that started during navigation. Both regressions first failed, then passed with conservative snapshot-only classification. HTTP and runtime errors remain fatal.
- 4077205345, cohort bookmark: fixed. Resolve the requested cohort against authorized navigation before mounting management hooks. Changing cohort updates the query and remounts cohort-local form/page state. Valid and unavailable bookmark regressions cover the target of invite, roster and enrollment requests.
- 4077206118, stale Teaching server: fixed. The dashboard hook resolves the requested ID against the loaded accessible-server list before fetching. It updates a stale bookmark to the effective selection and cannot display dashboard data belonging to an older request key. The regression verifies that no request uses the removed server.
- 4077206585, Inbox test setup: not reproduced. Vitest runs beforeEach before the test callback regardless of declaration order; the error override is inside that callback. The focused test passes locally and the hosted frontend suite passed at the reviewed head. No production change is justified by this suggestion.
- 4077207114, dialog scrolling: made explicit. Shared dialogs now specify vertical scrolling and contain overscroll. The browser scenario scrolls the actual details footer into view and clicks Close at phone, desktop and short-landscape sizes. Hosted execution must establish the result; source inspection alone does not.

## Custom suggestions

1,2,10: defer browser cache/sharding/reduced coverage. One worker deliberately shares the disposable seeded product environment. Splitting that environment is separate infrastructure work; reducing the core cross-browser coverage would weaken this issue's acceptance.

3: remaining manual, provider and combined-release gates already belong to #254, #251, #332 and #255 in the linked inventory and issue checklist. Keep those owners authoritative rather than copying another task list.

4,20: the inventory separates the historical accepted reconstruction from current integration changes; the changelog records incremental corrections. Consolidation is editorial follow-through after final acceptance, not evidence that those old results prove the current union.

5,8: retain the small per-test snapshot and failed-request collection. Each is scoped to one bounded Playwright test. A new generation abstraction or capped failure collection would add complexity and could discard evidence. The concrete ownership defect is fixed above.

6: the added announcement journey runs on the disposable hosted product stack, which is torn down after the job. It does not write to a persistent shared or production database.

7: retain explicit Home bootstrap response checks because the prior real-engine failure demonstrated that the heading/URL precede completed requests. The readiness checks are in the single existing login helper, with visible content assertions as well.

9,11: defer test configuration/tag refactors. The scenario list is bounded and the three-engine discovery/result establishes selected coverage; no missing scenario caused the observed defects.

12,13: defer legacy alias and enrollment decomposition. The small lazy redirect preserves query context; the enrollment boundary already prevents unauthorized manager hooks. Neither requires a new abstraction to correct the reviewed behavior.

14: removed the unconditional Teaching shortcut from the legacy header. The primary v2 navigation retains its capability-gated Teaching entry, and existing bookmarked routes remain valid.

15: renamed the component regression to native cancellation. Actual Escape is covered by the three-engine browser suite.

16: retain the mutation callback's controlled cache update in the component fixture; this intentionally simulates successful completion removing the final row. Real persistence now has a separate actual-service journey.

17,18,19: fixed with a single typed audience-copy map for event rows, details and editor descriptions.

## Account-data review and second remediation round

Full review completed at exact 14bfa5f3. Its account-data hosted browser failures
remain blocking; neither static checks nor this review establishes UI acceptance.

- 4077689414, sign-in fragment: fixed. The return destination preserves pathname,
  query and fragment. The expanded protected-route regression failed before the
  correction and passes afterward. Ordinary session-end redirects stay cleared.
- 4077707182, unused HTTPS listener: not reproduced. The separate CI signed-in
  journey step passes `PLAYWRIGHT_BASE_URL` with the HTTPS listener. All three
  real browser engines exercise registration, cookies and recovery there. The
  moderation audio command intentionally retains its separate existing listener.
  The script alone does not claim that its audio run verifies HTTPS cookies.

Additional suggestions: export test cache disposal now uses gcTime zero, and both
account stylesheets are formatted by rule with unchanged tokens. Dedicated action
hooks, bundle-loop memoization, RTP running-total/retention refactors, browser
sharding/caching and reduced smoke coverage are deferred. The bounded test has one
short-lived room and requires old stream counters to remain for its final check.
Inbox polling remains the minimal fix for a demonstrated delayed-delivery gap;
there is no accepted realtime invalidation contract to replace it. Existing
disposable-stack cleanup and strict browser-failure classification remain intact.
Teaching resets errors at request start; dialogs in this slice open through visible
controls. No reproduced stale error or deep-link dialog justifies a further change.
