# Owner access to cohort invites

The People page exposes cohort management to a Study Server Owner or the Course
Instructor. Enrollment and TA management use that same rule. Invite retrieval
instead checks only the instructor role, so an owner with a separately assigned
instructor sees an invite control that cannot succeed. Earlier enrollment tests
created the course as its owner, giving that account both roles and hiding the
mismatch.

The invite SELECT will use the existing `cohortHasPeopleManager` relationship:
an instructor role for the cohort's course, or an owner role for that course's
Study Server. Both branches remain in the statement that reads `invite_code`.
This avoids an authorization precheck followed by an unguarded secret read. The
repository method will name the people-management policy instead of implying
instructor-only access. The service retains the existing 404 for a missing cohort
and 403 for an unauthorized caller, with an accurate owner/instructor message.

No route, response, schema, invite format, frontend bundle, public gateway rule,
or infrastructure changes are needed. Learners and TAs do not gain invite access.
The owning community service remains responsible for authorization. The existing
authenticated-user and live moderation checks continue to run before the action.

The regression uses separate owner and instructor identities, an actual migrated
test database, the production repository and the controller/service request path.
It checks both authorized identities, enrolled learners, a TA, another server's
owner and instructor, an unrelated caller and a nonexistent cohort. The fixture
asserts that the tested owner has no course role. Large-cohort pagination and the
legacy enrollment redirect remain separate #339 work.

Scope: reopened #136, supporting #339 and #254. The launch owner coordinates
integration after exact-head CI and full CodeAnt review. Local tests use the
existing embedded database; hosted product checks provide the PostgreSQL context.
