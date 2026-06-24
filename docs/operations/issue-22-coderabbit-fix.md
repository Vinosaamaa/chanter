# Issue #22 — CodeRabbit fix log (post-merge)

Date: 2026-06-21  
Original PR: #43 (merged 2026-06-24)  
Follow-up branch: `fix/22-coderabbit-review`

## Findings addressed

| Severity | File | Action |
|----------|------|--------|
| Major | `OfficeHoursController.java` | Waitlist `Location` now points to `/office-hours/{sessionId}/waitlist` (schedule Location was already fixed in #43) |
| Major | `OfficeHoursRepository.java` | Status parameters use domain enums instead of raw strings |
| Major | `OfficeHoursService.java` | `rejoinWaitlistEntry` refreshes `joined_at`; `claimNextWaitingEntry` uses conditional update to reduce admit races |
| Major | `OfficeHoursService.java` | Waitlist list restricted to managers (`canManageOfficeHours`) |
| Major | `V5__office_hours_status_checks.sql` | DB `CHECK` constraints on session and waitlist status values |
| Major | `OfficeHoursSmokeTest.java` | Added non-enrolled join, learner cannot list waitlist, closed-window boundary tests |
| Minor | `App.tsx` | Clear waitlist when scheduling a new session; set session status to `LIVE` after manager voice join |

## Deferred

| Severity | File | Reason |
|----------|------|--------|
| Critical | `OfficeHoursActorRequest`, schedule/join body user ids | `TODO(#auth)` until #30 — same demo harness pattern as #16–#21 |
| Major | `JdbcCourseRepository.java` `can_manage` | Dedicated TA role deferred; instructors manage Office Hours for MVP (same as #21 TA queue) |
| Major | `issue-22-change-log.md` TA deferral | Documented; formal TA assignment is a later slice |
| Minor | `issue-22-change-log.md` PR header | Updated with merge reference in this follow-up |

## Verification

```bash
mvn -pl community-service verify
npm run lint && npm run build
```

Browser demo: schedule → learner waitlist → instructor admit + voice join.
