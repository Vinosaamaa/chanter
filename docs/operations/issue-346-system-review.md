# Issue 346 system review

Design review: optional creation input selects an already-supported first-cohort
policy. The request validates exact strings before enum conversion, including
rejecting coerced numeric/boolean input. Draft creation accepts omission/null but
rejects an explicit policy without a cohort. Existing owner authorization runs
before creation. No new endpoint, role, migration, dependency or runtime setting.

Implementation review: the controller passes the validated policy through the
service to the repository's existing transaction. The first cohort INSERT binds
it directly; course, role, cohort and default channels remain atomic. Existing
internal creation callers use the original service overload and retain OPEN.
The join switch and enrollment persistence are unchanged.

Verification: all eight CourseDiscoverySmokeTest cases pass against real MockMvc,
service and migrated H2. This includes correct/wrong/missing outsider invite,
OPEN member/outsider distinction, both unavailable policies, invalid input and
owner authorization. Full common/community Maven verify also passes: 161 cases,
zero failures/errors, three environment-dependent cases skipped. Hosted checks
remain pending. The
real #339 browser flow remains a separate integration gate, with no claim of
PostgreSQL repeated-join or production acceptance from these component tests.

The existing native release validation trigger omitted backend-only PRs, while
manual packaging is intentionally restricted to main. Add backend/** to the PR
path filter so these actual application changes run the existing two-architecture
build, scan and staging checks. Dispatch, publication and runtime guards remain
unchanged. No frontend change is used to manufacture a trigger.
