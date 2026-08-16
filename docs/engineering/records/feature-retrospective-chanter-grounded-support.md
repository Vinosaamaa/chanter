---
schemaVersion: 1
id: feature-retrospective-chanter-grounded-support
revision: 1
type: feature-retrospective
status: released
title: "Issue #16: Post support questions in course channels"
repository: chanter
capabilityIds: ["chanter-grounded-support"]
createdAt: 2026-06-18
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and workflow logs were not quoted.","A public launch was not evidenced; release truth remains strong local beta."]
modules: ["backend-community-service","backend-message-service","backend-gateway-service","frontend-development-demo"]
interfaces: ["backend/community-service/src/main/java/com/chanter/community/api/SupportQuestionChannelAccessResponse.java","backend/message-service/src/main/java/com/chanter/message/api/CreateSupportQuestionRequest.java","backend/message-service/src/main/java/com/chanter/message/api/SupportQuestionController.java","backend/message-service/src/main/java/com/chanter/message/api/SupportQuestionListResponse.java"]
seams: ["message-service-to-community-access","support-domain-to-postgresql","browser-demo-to-gateway"]
adapters: ["backend/community-service/src/main/java/com/chanter/community/api/CourseController.java","backend/community-service/src/main/java/com/chanter/community/infra/JdbcCourseRepository.java","backend/message-service/src/main/java/com/chanter/message/application/CourseChannelAccessClient.java","backend/message-service/src/main/java/com/chanter/message/application/SupportQuestionRepository.java"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Enrollment-aware support question submission","Instructor unanswered-question listing","Idempotent support-question writes"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Pull request #34","url":"https://github.com/Vinosaamaa/chanter/pull/34","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:34","head-commit:484845ca378c130cd2c556c770aeac7bc0e1a87b","merge-commit:d7667a6aed27efc9fdf2a8ad944ef3c3ee6e9618"]}
visibility: public-safe
publicationEligibility: eligible
issue: 16
pr: 34
release: null
run: null
---
# Issue #16: Post support questions in course channels

## Context

Support Questions needed to respect Course access while being owned by the service responsible for messages. Pull request #34 made that cross-service boundary explicit instead of trusting browser state or duplicating enrollment rules.

## Delivered boundary

The Community Service answered whether a caller could use a Course question channel. The Message Service consulted that boundary before accepting a Support Question, stored questions durably and idempotently, and exposed unanswered listing for instructors. Gateway routes and the frontend demo connected the complete learner-to-instructor path.

Authorization remained split by responsibility: Community owned Course and Enrollment truth; Message owned Support Question lifecycle. The HTTP access adapter was the seam between them.

## Verification

The PR records focused Community and Message verification, frontend lint and build checks, and a browser flow covering Course creation, Enrollment, learner submission, and instructor listing.

## Consequences

The slice created an auditable human-support path for later AI handoff work. It also introduced a runtime dependency from Message to Community access checks, making availability and denial behavior at that seam operationally important.

## Historical limits

The reconstructed record omits workflow and attachment contents and makes no claim that the local flow was publicly deployed.
