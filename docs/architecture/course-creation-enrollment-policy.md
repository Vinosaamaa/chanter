# Course creation enrollment policy

Repository: Vinosaamaa/chanter. Issue #346, prerequisite for #339 and #254;
related roster scope #136. Backend lane, registered issue-346 worktree, branch
`codex/346-course-enrollment-policy`, one issue-linked prerequisite PR.

The actual invitation journey creates an OPEN cohort. OPEN deliberately admits
only existing Study Server members, including when an outsider supplies a valid
invite. Creation currently offers no way to choose INVITE_ONLY without SQL.

Add optional `enrollmentPolicy` to the existing authenticated owner-only
`POST /api/v1/study-servers/{studyServerId}/courses` JSON body. Allowed strings are
OPEN, INVITE_ONLY, OPENING_SOON and CLOSED. Missing or null retains OPEN. An
explicit policy requires a nonblank cohortName; reject it with 400 for a draft
without a first cohort rather than discard the choice. Persist the policy in the
same existing repository transaction as the course, first cohort and channels.
The response and schema remain unchanged; the catalog already reports policy.

OPEN keeps the member gate. INVITE_ONLY requires the exact invite and permits an
authenticated outsider through that existing path. OPENING_SOON and CLOSED keep
their existing join denial. No policy update endpoint or permission is added.
#339 owns the visible choice: Invite link sends INVITE_ONLY; Study Server members
sends OPEN. Other API consumers keep their previous behavior by omitting it.

Acceptance uses actual MockMvc creation, database persistence, invite retrieval
and join requests: correct invite succeeds for an outsider, missing/wrong invites
fail, omitted OPEN still denies outsiders, closed policies deny, invalid input
and non-owner creation fail. Existing OPEN member and publication tests remain.
Repeated join transaction correction stays with #339; no enrollment write changes
belong here. Required hosted CI/review and later actual invitation journey remain
distinct from H2 component evidence. No local product server is needed.
