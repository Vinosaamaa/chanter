---
schemaVersion: 1
id: change-note-owner-cohort-invites
revision: 1
type: change-note
status: proposed
title: "Allow cohort people managers to retrieve invite links"
repository: chanter
capabilityIds: ["chanter-social-collaboration-mvp"]
createdAt: 2026-09-22
reconstructed: false
confidence: high
unknowns: ["Exact-head hosted review and merged-main acceptance remain required."]
modules: ["community-service"]
interfaces: ["cohort-invite"]
seams: ["people-management-to-invite-authorization"]
adapters: ["jdbc-course-repository"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Owner and instructor cohort invite access"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Cohort people management", "url":"https://github.com/Vinosaamaa/chanter/issues/136", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:CohortInviteAuthorizationTest", "test:CourseEnrollmentSmokeTest", "test:StudyServerNavigationSmokeTest"]}
visibility: public-safe
publicationEligibility: eligible
issue: 136
pr: null
release: null
run: null
---
# Allow cohort people managers to retrieve invite links

A Study Server Owner with a different Course Instructor could enroll learners
but could not retrieve the cohort invite shown by the People page. The invite
query now applies the same owner-or-instructor policy as people management,
scoped through the cohort's course and Study Server in the secret-reading SELECT.

The regression first reproduced the owner's 403, then passed with separate owner
and instructor identities. Learners, TAs, unrelated users and managers from other
servers/courses remain denied. No schema, public contract or frontend changes are
required. Design and system-review notes document the policy and test boundary.
