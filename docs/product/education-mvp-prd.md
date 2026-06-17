# Chanter Education MVP PRD

## Problem Statement

Educators who run cohort-based courses, bootcamps, tutoring groups, and large study communities often choose Discord because students already understand realtime chat, channels, voice rooms, roles, and bots. But Discord is not built as a learning product. Course knowledge gets buried in fast-moving channels, students repeat the same questions, office hours become manual queues, instructors cannot easily see which topics are confusing learners, and valuable peer explanations disappear into chat history.

Traditional LMS products solve structure and tracking, but they often feel static and disconnected from the daily community conversation where learning actually happens. Chanter should combine the energy of Discord-style communities with learning operations, AI teaching assistance, and instructor visibility.

## Solution

Build Chanter's first product wedge as an education-focused "Study Server" SaaS platform: a Discord-like community app for learning groups, with structured course spaces, AI Study Assistants, question workflows, office-hour operations, and learning analytics built in.

The first version should let educators create a learning community, organize course/module channels, upload learning resources, install a first-party AI Study Assistant into approved channels, and see actionable instructor insights. Learners should get a familiar chat experience plus better answers, searchable knowledge, summaries, and study support.

Chanter's positioning for this MVP is:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

## User Stories

1. As a course creator, I want to create a Study Server, so that my cohort has one organized place for chat, questions, resources, and live support.
2. As an instructor, I want to create module-specific channels, so that students discuss each part of the course in the right context.
3. As an instructor, I want to assign roles such as instructor, TA, student, and alumni, so that access and moderation match the learning community.
4. As an instructor, I want to pin or attach course resources to a channel, so that the AI Study Assistant and students can reference trusted material.
5. As a learner, I want to ask course questions in chat, so that I can get help without leaving the community.
6. As a learner, I want the AI Study Assistant to answer from approved course materials, so that answers are contextual and not generic.
7. As a learner, I want the AI Study Assistant to cite the source or channel context it used, so that I can trust and verify the answer.
8. As a learner, I want the assistant to say when it does not know, so that I do not rely on hallucinated course guidance.
9. As a learner, I want similar questions to be detected, so that I can find previous explanations before asking again.
10. As an instructor, I want repeated questions to become FAQ candidates, so that I can reduce repetitive support work.
11. As an instructor, I want to approve or edit generated FAQ entries, so that official course knowledge stays accurate.
12. As a TA, I want to see unanswered or low-confidence questions, so that I can prioritize human help.
13. As a student, I want to join an office-hours queue, so that I know when I will get live support.
14. As a TA, I want to manage the office-hours queue, so that support is fair and organized.
15. As an instructor, I want weekly summaries of each course channel, so that I can quickly understand what happened.
16. As a learner, I want summarized study notes from active discussions, so that I can review important concepts later.
17. As an instructor, I want to see common misconceptions, so that I can improve lessons or create clarifying posts.
18. As an instructor, I want engagement analytics, so that I can identify active learners and students who may be falling behind.
19. As an instructor, I want to see AI usage and cost indicators, so that I can manage my SaaS plan and avoid surprise usage.
20. As an admin, I want to control which channels the AI Study Assistant can read, so that private or sensitive channels remain protected.
21. As a learner, I want to see when an AI agent is present in a channel, so that I know when my messages may be used for context.
22. As an admin, I want audit logs for assistant answers, FAQ generation, and resource access, so that AI behavior is reviewable.
23. As an instructor, I want a clean dashboard of unanswered questions, repeated questions, office-hour load, and active topics, so that I can manage the course without reading every message.
24. As a learner, I want course search across resources, FAQs, summaries, and relevant messages, so that I can find answers quickly.
25. As a course business owner, I want a simple SaaS plan that includes community, AI assistance, and operations, so that I do not need Discord plus a separate LMS plus separate bots.

## Implementation Decisions

- The first product surface is an education-focused Study Server, not a generic public Discord clone.
- The first buyer is educators running cohort-based courses, bootcamps, tutoring groups, and study communities.
- The first business model is SaaS subscription tiers, with AI usage quotas and plan limits.
- The first AI capability is a first-party AI Study Assistant, not an open marketplace of third-party agents.
- The assistant is modeled as a visible, permissioned member of selected Study Server channels.
- The assistant can only read approved channels and approved resource collections.
- Course resources become a first-class concept that can power answers, FAQ generation, summaries, and search.
- Question workflows should distinguish AI-answered, human-answered, unanswered, duplicate, and FAQ-candidate states.
- Office-hours queueing is part of the education MVP because it creates concrete operational value for instructors and TAs.
- Instructor analytics should focus on actionable learning operations: unanswered questions, repeated questions, misconceptions, engagement, and AI usage.
- The architecture still uses the planned microservice boundaries: Community Service for servers/channels/roles/permissions, Message Service for durable chat, Media Service for resource attachments, Search Service for retrieval/search, Agent Service for assistant install/config, Agent Runtime Service for AI orchestration, Memory Service for summaries/retrieval, Safety Service for policy, and Billing Service for usage quotas.
- Marketplace, public agent publishing, paid third-party agents, and voice agents remain later phases after the first-party Study Assistant is trusted.

## Testing Decisions

- Tests should verify external behavior and user outcomes, not implementation details.
- Permission tests are critical: the AI Study Assistant must not access channels, resources, or messages outside its grants.
- Question workflow tests should cover unanswered, answered, duplicate, FAQ candidate, and human handoff states.
- Resource-grounded answer tests should verify that answers are constrained to approved resources and that low-confidence responses are handled safely.
- Office-hours tests should cover queue join, leave, next-student assignment, TA permissions, and auditability.
- Analytics tests should verify that events produce correct instructor-facing aggregates without querying another service's database directly.
- Billing and quota tests should verify AI usage metering, plan limits, and graceful quota exhaustion.
- E2E tests should cover a learner joining a Study Server, asking a question, receiving an assistant answer, joining an office-hours queue, and an instructor reviewing dashboard insights.
- Docker Compose smoke tests should eventually verify that the Study Server path works across frontend, gateway, core services, event broker, storage, and AI service stubs.

## Out of Scope

- Public marketplace for third-party agents.
- Paid creator payouts or revenue sharing for agent templates.
- Voice agent participation in live voice rooms.
- Full LMS replacement features such as graded assignments, gradebooks, SCORM import, accreditation workflows, and transcript issuance.
- Enterprise SSO, SCIM, compliance reporting, data residency, and custom contracts.
- Native mobile applications.
- Large-scale multi-region/cell deployment beyond preserving the planned service boundaries.

## Further Notes

The MVP should win by reducing educator workload and making learning communities easier to run. The strongest first demo is not "chat works"; it is "a student asks a repeated course question, the AI Study Assistant answers from approved resources, the instructor sees the repeated confusion in a dashboard, and the explanation becomes an approved FAQ/study note."

The product should still feel familiar to learners who like Discord, but the buyer value should be framed around outcomes: fewer repeated questions, faster support, better knowledge retention, higher engagement, and lower instructor/TA overhead.

