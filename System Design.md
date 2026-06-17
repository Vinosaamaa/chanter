# System Design

## Backend Architecture Overview

This backend is designed as a cell-based microservice architecture for a real-time community/chat platform. The first product wedge is an education SaaS product: Discord-like Study Servers for courses, bootcamps, tutoring groups, and learning communities, with AI teaching assistants and instructor operations built in. The goal is to support a local production-ready education MVP first, while preserving architectural boundaries that can scale toward very large traffic later.

![Backend architecture overview](docs/diagrams/system-backend-architecture.drawio.png)

Editable source: [`system-backend-architecture.drawio`](docs/diagrams/system-backend-architecture.drawio) | PNG export: [`system-backend-architecture.drawio.png`](docs/diagrams/system-backend-architecture.drawio.png)

## Product Architecture Direction

The initial product is a Study Server:

- Educators create Study Servers for cohorts or courses.
- Study Servers contain course/module channels, announcements, support channels, and office-hours workflows.
- Instructors and TAs manage roles, resources, questions, FAQ entries, and support queues.
- Learners use a familiar Discord-like chat experience while getting better answers, summaries, search, and human handoff.
- The first-party AI Study Assistant is a visible, permissioned member that can answer only from approved resources and allowed context.
- Instructor dashboards expose actionable learning operations: unanswered questions, repeated questions, misconceptions, engagement, office-hours load, and AI usage.

The system should not begin as a full LMS replacement. Gradebooks, SCORM import, accreditation workflows, public agent marketplace, enterprise SSO/compliance, and voice agents are later phases.

## Architecture Layers

### Client And Edge Layer

Users access the application through a web or mobile client. Static frontend assets are served through a CDN. Traffic then passes through global routing, WAF/DDoS protection, edge rate limiting, and optional edge JWT validation before reaching backend services.

The edge layer exists to keep obvious bad traffic away from the application, route users to a healthy region, reduce latency, and avoid sending static asset traffic to backend services.

### Service Layer

The backend uses Spring Boot microservices. Each service owns one business capability and should be deployable independently.

- Gateway Service owns public REST routing, CORS, edge rate limiting coordination, request correlation, and routing to internal services.
- Auth Service owns registration, login, password hashing, access tokens, refresh token rotation, sessions, and logout.
- User Service owns profiles, display names, avatars metadata, user settings, and account status.
- Community Service owns Study Servers, course/module channels, members, instructor/TA/learner roles, permissions, invites, and canonical permission evaluation.
- Message Command Service owns message writes, edit/delete commands, question markers, reactions, read receipts, idempotency keys, and durable message creation.
- Message Query Service owns message reads, pagination, history lookup, query-optimized message views, and course-channel history views.
- Realtime WebSocket Gateway owns connected clients, subscriptions, channel authorization, reconnects, typing indicators, and presence fan-out.
- Fanout Service consumes durable events and pushes them to the correct realtime gateway nodes.
- Notification Service owns mentions, unanswered-question alerts, unread counts, notification preferences, and delivery state.
- Moderation Service owns bans, kicks, reports, warnings, audit logs, and moderation workflows.
- Media Service owns upload sessions, attachment metadata, course resources, validation policy, object storage integration, signed download authorization, and AI-approved resource status.
- Search Service owns denormalized search indexes for messages, users, channels, Study Servers, approved FAQs, course resources, and summaries.
- Analytics Service owns event ingestion, aggregates, usage metrics, instructor dashboard read models, misconception signals, and office-hours load metrics.
- Agent Service owns agent definitions, personas, Study Assistant installs, channel bindings, requested permissions, model settings, resource grants, and agent lifecycle.
- Agent Runtime Service owns prompt assembly, model routing, grounded answer attempts, tool calls, streaming responses, and conversation orchestration.
- Voice Agent Service owns voice agent sessions, speech-to-text, text-to-speech, voice room participation, and transcript events.
- Memory Service owns opt-in agent memory, summaries, embeddings, retrieval, retention policy, and deletion workflows.
- Marketplace Service owns agent listings, creator publishing, installs, versioning, reviews, and marketplace governance.
- Billing Service owns SaaS plans, credits, subscriptions, AI usage metering, quotas, provider cost attribution, and paid agent purchases later.
- Safety Service owns prompt-injection detection, content policy checks, output review hooks, abuse signals, and agent evaluation records.

### Event Plane

Cross-service workflows use an event broker such as Redpanda locally or Kafka-compatible infrastructure in production.

Important events include:

- `UserRegistered`
- `UserProfileUpdated`
- `ServerCreated`
- `StudyServerCreated`
- `MemberJoinedServer`
- `RoleChanged`
- `ChannelCreated`
- `CourseResourceApproved`
- `MessageCreated`
- `MessageEdited`
- `MessageDeleted`
- `QuestionDetected`
- `QuestionAnswered`
- `QuestionMarkedDuplicate`
- `FaqCandidateCreated`
- `FaqEntryApproved`
- `OfficeHoursQueueJoined`
- `OfficeHoursQueueItemResolved`
- `ReactionAdded`
- `AttachmentUploaded`
- `MentionDetected`
- `ModerationActionCreated`
- `AgentInstalled`
- `AgentRemoved`
- `AgentInvoked`
- `AgentResponseCreated`
- `StudyAssistantLowConfidence`
- `StudyAssistantGroundedAnswerCreated`
- `AgentToolCalled`
- `AgentMemoryCreated`
- `AgentMemoryDeleted`
- `AiUsageMetered`
- `VoiceAgentJoined`
- `VoiceAgentTranscriptCreated`
- `MarketplaceAgentPublished`
- `AgentPurchaseCompleted`

Critical writes should use the outbox pattern. A service writes its business data and an outbox record in the same database transaction. An outbox publisher then publishes the event to the broker. This prevents the system from storing a message but failing to notify downstream services permanently.

Consumers must be idempotent because events can be delivered more than once.

### Data Plane

Each service owns its own database or storage system. Other services do not query that database directly.

- Auth data is partitioned by `userId`.
- User profile data is partitioned by `userId`.
- Community data is partitioned by `serverId`.
- Message data is partitioned by `channelId`, with time buckets for hot channels.
- Notification data is partitioned by `userId`.
- Search data is stored in a search cluster or search read model.
- Analytics data is stored in a data lake or OLAP system.
- Media binaries are stored in object storage, while metadata stays in Media Service storage.
- Agent configuration is stored by server, channel, installed listing version, and permission grant.
- Education MVP data is owned by the service responsible for the action: Study Server structure and roles live in Community Service, durable questions and messages live in Message Service, course resource metadata lives in Media Service, FAQ/search read models live in Search Service, instructor insight read models live in Analytics Service, assistant installs/grants live in Agent Service, grounded answer attempts live in Agent Runtime Service, and plan/quota state lives in Billing Service.
- Agent memory is stored in a scoped vector store and summary store with explicit retention and deletion policies.
- Marketplace data stores listings, versions, creator profiles, installs, reviews, and governance state.
- Billing data stores usage meters, credit balances, subscriptions, provider costs, and budget limits.
- Safety audit data stores policy decisions, prompt-injection signals, model evaluations, and tool-use audit records.
- Redis stores cache, sessions, rate limit counters, presence, and typing state.

Redis is not the durable source of truth. It is used for fast ephemeral or cache-oriented data.

### Control Plane

The control plane manages configuration, secrets, service discovery, CI/CD, observability, and alerting.

Every service should emit structured logs, metrics, health checks, readiness checks, and distributed traces. Every user action should carry a correlation ID across Gateway, services, event broker, and realtime delivery.

## Design Maintenance Workflow

Use installed Cursor workflow skills directly when changing this design:

- Use `grill-with-docs` before making durable architecture decisions so assumptions, tradeoffs, and missing requirements are challenged.
- Use `to-prd` when a system capability needs product goals, non-goals, acceptance criteria, and a test plan.
- Use `to-issues` to split large architecture work into vertical slices that can be reviewed independently.
- Use `zoom-out` or `improve-codebase-architecture` after each major milestone to check service boundaries, data ownership, event flow, permissions, reliability, and observability.
- Use `diagnose` for bugs, failures, performance issues, or flaky tests once code exists.

## Critical Message Write Path

The message write path must stay small, durable, and fast.

![Critical message write path](docs/diagrams/system-critical-message-write-path.drawio.png)

Editable source: [`system-critical-message-write-path.drawio`](docs/diagrams/system-critical-message-write-path.drawio) | PNG export: [`system-critical-message-write-path.drawio.png`](docs/diagrams/system-critical-message-write-path.drawio.png)

The synchronous path should only do the work needed to safely accept a message: authenticate, authorize, persist, and acknowledge. Fan-out, notifications, search indexing, and analytics happen asynchronously.

## AI Agent Platform

AI agents are modeled as special permissioned members of a server or channel. They are not hidden background processes. If an agent can read a channel, join a voice room, remember context, use tools, or perform moderation assistance, that capability must be visible and explicitly granted.

The first production agent is the AI Study Assistant for Study Servers. It answers learner questions from approved course resources, approved FAQ entries, and allowed recent context; identifies low-confidence questions; and routes unresolved support to TAs or office-hours queues.

Future agent examples:

- Study assistant that explains topics, quizzes members, and summarizes lessons.
- Meeting assistant that joins voice channels, transcribes discussion, summarizes decisions, and creates action items.
- Moderator assistant that flags spam, suspicious links, raids, or toxic content for human review.
- Game master agent for roleplay, trivia, campaigns, or community events.
- Server helper that answers questions from rules, pinned posts, FAQs, and documents.
- Translator agent that translates messages across languages.
- Character agent with a specific personality, voice, avatar, and response style.
- Coding assistant for programming communities.

The platform should start with the built-in AI Study Assistant first. The marketplace should come later after permissions, memory, safety, billing, and quality controls are proven.

### Agent Invocation Path

![Agent invocation path](docs/diagrams/system-agent-invocation-path.drawio.png)

Editable source: [`system-agent-invocation-path.drawio`](docs/diagrams/system-agent-invocation-path.drawio) | PNG export: [`system-agent-invocation-path.drawio.png`](docs/diagrams/system-agent-invocation-path.drawio.png)

The important rule is that agent responses become normal durable messages through Message Service. The runtime can stream partial output to the UI, but the final answer should be stored and delivered through the same message pipeline as user messages. For education workflows, every grounded answer should also record resource scope, confidence state, safety state, and usage metering so instructors can audit and manage the assistant.

### Voice Agent Path

![Voice agent path](docs/diagrams/system-voice-agent-path.drawio.png)

Editable source: [`system-voice-agent-path.drawio`](docs/diagrams/system-voice-agent-path.drawio) | PNG export: [`system-voice-agent-path.drawio.png`](docs/diagrams/system-voice-agent-path.drawio.png)

Voice agents should be introduced carefully because they add privacy, latency, and cost concerns. A voice room must clearly show when an agent is present, whether transcription is enabled, and whether summaries or memories are being saved.

### Agent Memory And Tools

Agent memory is opt-in and scoped.

Memory scopes:

- No memory: agent only sees the current prompt.
- Recent context: agent can read recent messages in the channel for a short window.
- Channel summaries: agent can use generated summaries for that channel.
- Server knowledge base: agent can use approved documents, FAQs, rules, and pinned content.
- Personal memory: only if explicitly enabled by the user and allowed by policy.

Tool examples:

- Search approved course resources.
- Search approved Study Server FAQ.
- Summarize course channel discussions.
- Suggest FAQ entries from repeated questions.
- Route low-confidence answers to office hours.
- Summarize thread.
- Summarize voice meeting.
- Create poll.
- Create action items.
- Translate message.
- Flag message for moderator review.
- Search approved files or documents.

Every tool needs a permission grant. High-risk tools should require admin approval and may require confirmation before execution.

### Agent Marketplace

The marketplace should sell or distribute installable agent templates after the internal agent platform is stable.

Marketplace items can include:

- Agent personalities.
- Specialized assistants.
- Voice packs.
- Prompt packs.
- Server moderation agents.
- Game and event agents.
- Tool integrations.
- Premium model tiers.

Marketplace controls:

- Listing review before public publishing.
- Versioned agent definitions.
- Permission disclosure before install.
- Creator identity and reputation.
- Reviews and ratings.
- Abuse reporting.
- Billing, refunds, credits, and creator payouts.
- Sandboxed tools so marketplace agents cannot run arbitrary backend actions.

## AI Agent Rollout Phases

Phase 1: AI Study Assistant

- Install the AI Study Assistant into selected Study Server channels.
- Mention or ask the assistant in a course channel.
- Let it answer using approved course resources, approved FAQs, and allowed recent context.
- Add low-confidence handoff to TAs or office-hours queues.
- Add permission checks, usage limits, audit logs, and safety checks.

Phase 2: Memory and server knowledge

- Add opt-in channel summaries.
- Add Study Server FAQ and course-resource retrieval.
- Add memory deletion and retention controls.
- Add instructor/admin UI for assistant memory, resource access, and channel access.

Phase 3: Voice agent

- Let an agent join a voice channel.
- Add speech-to-text transcription.
- Add meeting summaries and action items.
- Add spoken responses with text-to-speech.
- Make transcription and memory state visible to all participants.

Phase 4: Tools and moderation assistance

- Add approved tools for search, summarize, translate, poll creation, and moderator review.
- Keep tool execution sandboxed.
- Require explicit permission grants for each tool.
- Add human approval for sensitive actions.

Phase 5: Marketplace

- Add private marketplace listings first.
- Add creator publishing, reviews, installs, and listing versions.
- Add paid agents, subscriptions, credits, and quotas.
- Add marketplace review and abuse-report workflows before opening public publishing.

## Scaling Principles

The large-scale version uses the same service boundaries but deploys them as regional cells.

A cell is a mostly self-contained deployment unit with its own gateway fleet, realtime fleet, service replicas, Redis cluster, event broker partitions, and database shards. This improves failure isolation because one unhealthy cell should not take down the entire platform.

Key scaling decisions:

- Route users to nearby healthy regions.
- Assign communities or channels to home regions.
- Write messages to the channel's home cell.
- Replicate events cross-region only when needed.
- Shard service databases by ownership keys.
- Split message command and query paths.
- Use fan-out services instead of pushing directly from Message Service.
- Use event-driven read models for search, notifications, and analytics.
- Treat presence and typing as ephemeral, while messages remain durable.
- Route AI calls through a Model Gateway so cost, rate limits, fallbacks, and provider failures are controlled centrally.
- Queue non-urgent AI work such as long summaries, embeddings, meeting notes, and marketplace safety evaluations.
- Track AI cost and latency per server, channel, user, agent, model, and marketplace listing.

## Consistency Model

Strong consistency is required for:

- Login and refresh token rotation.
- Password and account security changes.
- Membership changes.
- Role and permission changes.
- Channel access checks.
- Message writes within a channel.
- Moderation actions that restrict access.
- Course resource access grants.
- Office-hours queue state transitions.
- Agent install permissions, Study Assistant resource grants, tool permissions, billing limits, memory deletion, and marketplace purchase state.

Eventual consistency is acceptable for:

- Search indexing.
- Analytics dashboards.
- Instructor insight dashboards.
- Notification badge counts.
- FAQ suggestions before instructor approval.
- Profile display updates.
- Cross-region presence.
- Some read receipt updates.
- Embeddings, long summaries, transcript indexing, marketplace analytics, and recommendation ranking.

The system should not try to make every operation globally strongly consistent. That would increase latency and reduce reliability. Instead, each feature should explicitly choose the consistency level it needs.

## Reliability Rules

The system should assume partial failure is normal.

- Every network call needs a timeout.
- Retries are allowed only for safe or idempotent operations.
- Message sends, uploads, moderation actions, and event consumers need idempotency.
- Question detection, FAQ generation, office-hours queue actions, and AI Study Assistant invocations need idempotency.
- Service-to-service calls need circuit breakers.
- Event consumers need dead-letter topics.
- WebSocket delivery needs backpressure.
- Search, analytics, and notifications should degrade without breaking chat.
- Clients should recover missed durable events by querying Message Query Service after reconnect.
- LLM provider failures should degrade agent responses without impacting core chat.
- Low-confidence or failed Study Assistant answers should route to human help rather than blocking the learner's message flow.
- Agent tool calls must be idempotent, audited, and sandboxed.
- Agent memory deletion must be reliable and should not depend on eventually consistent index cleanup alone.

## Core Backend Rule

The most important backend rule is: the message write path must remain small and durable.

Sending a message should authenticate the user, check permissions, persist the message, publish an event, and return success. Search, analytics, notifications, and realtime fan-out should happen after the durable write through asynchronous events. This keeps the core chat experience fast and reliable under heavy traffic.

The most important AI rule is: agents must be permissioned, visible, auditable, and cost-controlled.

An agent should only read channels, use tools, remember context, join voice, or perform actions after explicit grants. For the education MVP, the AI Study Assistant should only answer from approved resources and allowed context, should make uncertainty visible, and should support human handoff. Marketplace agents should be treated as untrusted templates until reviewed, sandboxed, versioned, and installed with clear permission disclosure.
