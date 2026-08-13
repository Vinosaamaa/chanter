---
schemaVersion: 1
id: feature-retrospective-chanter-social-collaboration-mvp
revision: 1
type: feature-retrospective
status: released
title: "Issue #15: Send Friend Request And Direct Message"
repository: chanter
capabilityIds: ["chanter-social-collaboration-mvp"]
createdAt: 2026-06-18
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and workflow logs were not quoted.","A public launch was not evidenced; release truth remains strong local beta."]
modules: ["backend-message-service","backend-gateway-service","frontend-development-demo"]
interfaces: ["backend/message-service/src/main/java/com/chanter/message/api/CreateDirectMessageRequest.java","backend/message-service/src/main/java/com/chanter/message/api/CreateFriendRequestRequest.java","backend/message-service/src/main/java/com/chanter/message/api/CreateUserBlockRequest.java","backend/message-service/src/main/java/com/chanter/message/api/DirectMessageResponse.java","backend/message-service/src/main/java/com/chanter/message/api/FriendRequestResponse.java"]
seams: ["gateway-to-message-service","social-domain-to-postgresql","browser-demo-to-gateway"]
adapters: ["backend/message-service/src/main/java/com/chanter/message/api/SocialMessagingController.java","backend/message-service/src/main/java/com/chanter/message/application/SocialMessagingRepository.java","backend/message-service/src/main/java/com/chanter/message/infra/JdbcSocialMessagingRepository.java"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Friend request lifecycle","Friendship removal and blocking","Durable direct messages"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Pull request #33","url":"https://github.com/Vinosaamaa/chanter/pull/33","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:33","head-commit:799404884d8f9a30b451e531096f5063a6e2f185","merge-commit:2671718b0d2b4baed92e45c836924932445ff5c4"]}
visibility: public-safe
publicationEligibility: eligible
issue: 15
pr: 33
release: null
run: null
---
# Issue #15: Send Friend Request And Direct Message

## Context

The education MVP needed a platform-wide social baseline separate from Course and Cohort access. Pull request #33 introduced friendship and Direct Message behavior while keeping the frontend surface explicitly framed as a development demonstration.

## Delivered boundary

The Message Service became the owner of friend requests, accepted friendships, blocks, friendship removal, and durable Direct Messages. REST endpoints exposed that behavior through the gateway, and PostgreSQL migrations encoded the underlying social state. Duplicate or already-satisfied requests were rejected instead of silently creating competing rows.

The browser demo exercised request, acceptance, message, inbox, removal, and guard flows. This slice did not claim the later production Friends Hub, realtime delivery, or voice calling experience.

## Verification

The PR records Message Service smoke coverage, frontend lint and build checks, and a local end-to-end browser flow. The stored head and merge identities bind this reconstruction to the reviewed change.

## Consequences

Social policy became a Message Service concern rather than frontend-only state. That provided a durable base for later Friends Hub and realtime work, while leaving cross-role education policy and richer moderation to later slices.

## Historical limits

Workflow logs and attachment bodies were not quoted. The evidence supports a local product capability, not a public launch.
