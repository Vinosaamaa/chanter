---
schemaVersion: 1
id: feature-retrospective-chanter-realtime-social-voice
revision: 1
type: feature-retrospective
status: released
title: Bootstrap realtime service and live course channel chat (#51)
repository: chanter
capabilityIds: ["chanter-realtime-social-voice"]
createdAt: 2026-06-25
reconstructed: true
confidence: high
unknowns: ["Attachment bodies and workflow logs were not quoted.","A public launch was not evidenced; release truth remains strong local beta."]
modules: ["backend-community-service","backend-message-service","backend-realtime-service","backend-gateway-service","frontend"]
interfaces: ["backend/community-service/src/main/java/com/chanter/community/api/CourseChannelMessageAccessResponse.java","backend/community-service/src/main/java/com/chanter/community/api/StudyServerChannelMessageAccessResponse.java","backend/message-service/src/main/java/com/chanter/message/api/ChannelMessageController.java","backend/message-service/src/main/java/com/chanter/message/api/ChannelMessageListResponse.java"]
seams: ["realtime-session-to-http-identity","message-service-to-community-access","browser-history-to-websocket-events","gateway-to-realtime-service"]
adapters: ["backend/community-service/src/main/java/com/chanter/community/api/StudyServerChannelController.java","backend/message-service/src/main/java/com/chanter/message/application/ChannelMessageAccessClient.java","backend/message-service/src/main/java/com/chanter/message/infra/HttpChannelMessageAccessClient.java"]
relatedRecords: []
decisions: []
incidents: []
features: []
capabilities: ["Authenticated channel subscriptions","Durable permission-aware channel messages","Live fan-out with reconnect reconciliation"]
amends: []
supersedes: []
learningRefs: []
sources: [{"label":"Pull request #68","url":"https://github.com/Vinosaamaa/chanter/pull/68","kind":"pull-request"}]
verification: {"state":"verified","evidenceRefs":["pull-request:68","head-commit:f944ad1372af7eae718ae730a3f403177658bdd8","merge-commit:3a11936ed940cc86826698bd50b130f7d5a2f18f"]}
visibility: public-safe
publicationEligibility: eligible
issue: 51
pr: 68
release: null
run: null
---
# Bootstrap realtime service and live course channel chat (#51)

## Context

Course chat required both durable history and low-latency delivery. Pull request #68 separated those responsibilities while keeping authorization grounded in Community-owned membership and Course access.

## Delivered boundary

The Message Service added permission-aware list and post operations for Study Server and Course channels. The Realtime Service added authenticated WebSocket subscription, send, and fan-out behavior. Gateway routing exposed both paths, and the frontend combined historical fetches with optimistic send, resubscription, and missed-event reconciliation.

This slice covered live text chat. Despite the broader cluster name, it did not itself deliver media transport or establish a public production environment.

## Verification

The PR records focused tests across Community, Message, Realtime, and Gateway services plus frontend lint and build checks. Its manual plan covered live posting, cross-tab fan-out, and reconnect behavior.

## Consequences

The design avoided treating WebSocket delivery as the durable source of truth: history remained queryable through Message Service APIs. It also created an identity seam between HTTP and WebSocket entry paths that later security work needed to preserve.

## Historical limits

Attachment bodies and workflow logs were not quoted. The evidence supports a verified local capability, not a public launch.
