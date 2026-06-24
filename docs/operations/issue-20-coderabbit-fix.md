# Issue #20 — CodeRabbit fix log

Date: 2026-06-24  
Original PR: #38 (merged early before CodeRabbit completed)  
Follow-up branch: `fix/20-coderabbit-review`

## Review method

Retroactive `coderabbit review --agent --base b25a111` on the #20 diff (16 findings).

## Findings addressed

| Severity | File | Action |
|----------|------|--------|
| Major | `ApprovedFaqService.java` | `@Transactional` on `createOrUpdateApprovedFaq`; `LinkedHashSet` preserves source question order |
| Major | `ApprovedFaqService.java` | Batch `findIdsByChannelIdAndIds` replaces N+1 validation loop |
| Major | `GroundedSupportQuestionService.java` | FAQ list wrapped in try/catch; skip null question/answer pairs |
| Minor | `ApprovedFaqController.java` | `Location` header points to created FAQ id |
| Minor | `JdbcApprovedFaqRepository.java` | Escape `%` and `_` in FAQ search `LIKE` patterns |
| Minor | `HANDOFF.md`, `README.md` | Resolve #20 status contradiction (merged + follow-up PR) |
| Trivial | `ApprovedFaqSmokeTest.java` | Assert `Location` header includes created FAQ id |

## PR #40 follow-up (second CodeRabbit pass)

| Severity | File | Action |
|----------|------|--------|
| Major | `GroundedSupportQuestionService.java` | Swallow `BAD_GATEWAY` when FAQ list fails (supplemental grounding) |
| Trivial | `ApprovedFaqSmokeTest.java` | Assert `Location` header includes created FAQ id |

## Deferred

| Severity | File | Reason |
|----------|------|--------|
| Trivial | `V4__create_approved_faq_tables.sql` | `ON DELETE CASCADE` on join FK deferred — H2/Postgres constraint naming differs; RESTRICT acceptable for MVP |
| Minor | `ApprovedFaqController.java` | `@NotNull` on `@RequestParam` deferred — no `@Validated` controllers project-wide |
| Trivial | `FaqCandidateGrouper.java` | `LinkedHashSet` vs `HashSet` for token sets — negligible at MVP scale |
| Trivial | `JdbcApprovedFaqRepository.java` | Batch insert for join table — small bounded source lists in MVP |
| Minor | `education-mvp-issue-breakdown.md` | #20 already marked Done |

## Verification

```bash
mvn -pl message-service,agent-service verify
npm run lint && npm run build
```

Browser E2E: FAQ candidates → approve → search → assistant cites FAQ (demo harness).
