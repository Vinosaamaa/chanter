# Owner cohort invite follow-up

The residual #136 defect was found during #339's read-only legacy enrollment
comparison. An owner without the course's instructor role is permitted to manage
people but is rejected when retrieving the cohort invite.

The design is recorded in `issue-136-owner-invite-design.md`. The invite SELECT
now matches the existing owner-or-instructor people-management predicate and
reads the invite in that same statement. Internal method and actor names reflect
the policy. The existing route, response, 403 and 404 behavior are retained.

The new regression failed with the expected owner 403 before the change. After
the change, all nine invite, enrollment and navigation tests pass against the
migrated embedded database. The test separates owner and instructor identities,
asserts the owner has no course role, and covers unrelated server ownership,
unrelated course instruction, learner and TA denial. It exercises the real
controller/service/repository path and verifies repository results directly.

Exact-head hosted CI and full CodeAnt disposition will be attached to the PR.
Those gates and merged-main acceptance are not inferred from local results.
