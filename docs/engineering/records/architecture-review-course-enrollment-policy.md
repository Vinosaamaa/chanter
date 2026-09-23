---
schemaVersion: 1
id: architecture-review-course-enrollment-policy
revision: 1
type: architecture-review
status: proposed
title: "Choose first-cohort enrollment policy during course creation"
repository: chanter
capabilityIds: ["chanter-ui-v2"]
createdAt: 2026-09-23
reconstructed: false
confidence: high
unknowns: ["Exact-head hosted gates and the real #339 invitation journey remain required."]
modules: ["community-service"]
interfaces: ["course-creation"]
seams: ["course-to-first-cohort"]
adapters: ["spring-mvc", "jdbc"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Course enrollment"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Course enrollment policy", "url":"https://github.com/Vinosaamaa/chanter/issues/346", "kind":"issue"}]
verification: {"state":"verified", "evidenceRefs":["test:CourseDiscoverySmokeTest"]}
visibility: public-safe
publicationEligibility: eligible
issue: 346
pr: null
release: null
run: null
---
# Choose first-cohort enrollment policy during course creation

The first cohort policy belongs in the existing owner-authorized course creation
transaction. Its existing column supports all four policies; no migration,
permission, join rule or second update request is needed. Omitted input stays
OPEN and explicit input without a cohort rejects rather than disappearing.

HTTP tests exercise creation, persisted policy, invite retrieval, outsider join,
invalid inputs, absent authentication and non-owner denial. Existing publication
and OPEN member tests remain. The frontend choice and repeated PostgreSQL join
correction remain #339-owned, with actual browser integration still required.
