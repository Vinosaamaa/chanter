# Chanter Education MVP PRD

> **Product showcase:** screens, mockups, and click-flow diagrams live in [`docs/product-design/`](../product-design/README.md).  
> **Visibility model:** global friends + per-user course sidebar — [`visibility-and-social-model.md`](../product-design/visibility-and-social-model.md).

## Problem Statement

Educators who run cohort-based courses, bootcamps, tutoring groups, and large study communities often choose Discord because students already understand realtime chat, channels, voice rooms, roles, and bots. But Discord is not built as a learning product. Course knowledge gets buried in fast-moving channels, students repeat the same questions, office hours become manual queues, instructors cannot easily see which topics are confusing learners, and valuable peer explanations disappear into chat history.

Traditional LMS products solve structure and tracking, but they often feel static and disconnected from the daily community conversation where learning actually happens. Chanter should combine the energy of Discord-style communities with learning operations, AI teaching assistance, and instructor visibility.

## Solution

Build Chanter's first product wedge as an education-focused Study Server SaaS platform: a Discord-like community app for learning groups, with structured course spaces, Voice Channels, friend Direct Messages, AI Study Assistants, Support Question workflows, TA Queue and Office Hours operations, and Instructor Dashboard insights built in.

The first version should let educators create a Study Server, organize Courses and Cohorts, create Course Channels and optional Cohort Channels, upload Course Resources, install a first-party AI Study Assistant into approved channels, and see actionable instructor insights. Learners should get a familiar chat and voice experience plus better answers, searchable knowledge, Channel Summaries, and study support.

Chanter's positioning for this MVP is:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

## Market Reality And Why Not Discord

This product will not win by being "Discord but slightly different." Discord is free, familiar, fast, socially sticky, and already loved by students. A broad consumer social/chat product would be a weak business wedge because users have little reason to move and because network effects strongly favor existing platforms.

Chanter can be commercially viable only if it sells to the buyer's pain, not the learner's habit. The buyer is the educator, bootcamp, tutoring business, course creator, or learning organization that is already using Discord plus extra tools and still struggles with repeated questions, buried knowledge, manual office-hour logistics, weak course analytics, fragmented resources, and uncontrolled bots.

The reason to choose Chanter over Discord is not better chat. It is a complete learning operations layer:

- Fewer repeated questions because the AI Study Assistant answers from approved Course Resources and Approved FAQs.
- Less instructor/TA workload because Support Questions, low-confidence AI answers, and TA Queue items are routed into workflows.
- Better knowledge retention because channel discussions become Channel Summaries, FAQ candidates, and searchable study notes.
- Better buyer visibility because Instructors can see confusing topics, active learners, Office Hours load, and AI usage on the Instructor Dashboard.
- Safer AI because the assistant is visible, permissioned, resource-scoped, auditable, and quota-controlled instead of being a random bot with broad server access.
- Simpler SaaS buying because community, course resources, AI assistance, and support operations are in one product.

If a learning community only needs casual chat, Discord is the better choice. Chanter should target communities where support load, learning outcomes, and instructor operations matter enough to justify paying.

## Later Monetization Direction: Creator Course Commerce

After the education MVP works, Chanter can become more than a study-focused Discord. A strong later phase is creator course commerce: instructors can sell Courses directly inside a Study Server, and learners can enroll in a Cohort without leaving the community.

This would make Chanter closer to a community-native course platform: part Discord-style realtime community, part Circle/Skool-style paid learning community, part Udemy-like course storefront, and part AI-assisted learning operations system.

Potential later capabilities:

- Instructor course storefront inside a Study Server.
- Paid course listings with landing pages, curriculum outline, pricing, refunds, and enrollment status.
- Enrollment in a Cohort unlocks the correct Study Server, channels, resources, Live Class schedule, AI Study Assistant access, and Office Hours policies.
- Built-in Live Class rooms for cohort sessions, workshops, tutoring, and Q&A.
- Recordings, transcripts, summaries, and action items after Live Classes.
- Instructor revenue dashboard for sales, active learners, refunds, churn, and AI usage costs.
- Platform take-rate, subscription fees, or hybrid pricing.

This is not the first MVP because payments, refunds, taxes, creator trust, content moderation, fraud, and live-video reliability add significant scope. But it is a credible business expansion if Study Servers prove valuable.

## User Stories

### Study Server, Course, And Cohort

1. As a Study Server Owner, I want to create a Study Server, so that my learning community has one organized home.
2. As a Study Server Owner, I want to create Courses inside my Study Server, so that I can run multiple learning products in one community.
3. As an Instructor, I want to create Cohorts for a Course, so that scheduled groups of learners can run on different timelines.
4. As an Instructor, I want to enroll a learner in a Cohort, so that they get access only to the spaces for that group.
5. As a Study Server Owner, I want to assign Instructors to Courses, so that teaching responsibilities are clear.

### Channels, Voice, And Community

6. As an Instructor, I want Study Server Channels for announcements and community discussion, so that server-wide communication is separate from course work.
7. As an Instructor, I want Course Channels for each Course, so that learners discuss material in the right context.
8. As an Instructor, I want to optionally create a Cohort Channel, so that one Cohort can have a private space when needed.
9. As a Study Server Member, I want to join Voice Channels, so that I can talk with others in real time like Discord.
10. As a learner, I want to send a Friend Request and Direct Message another user, so that I can socialize privately on the platform.
11. As a learner, I want to accept or decline Friend Requests, so that I control who can message me.

### Roles And Access

12. As an Instructor for one Course, I want to be a Learner in another Course in the same Study Server, so that roles match how I actually participate.
13. As an Instructor, I want to assign TAs to a Cohort, so that support staff are scoped to the right learner group.
14. As a Study Server Owner, I want membership to require invite or Cohort Enrollment, so that server-wide channels are not public to strangers.
15. As an Instructor, I want Alumni to keep limited read access only where I allow, so that graduates do not retain active Cohort access by default.
16. As an Instructor, I want to invite Guests to specific spaces temporarily, so that visitors can participate without full membership.

### Course Resources And Search

17. As an Instructor, I want to attach Course Resources to a Course, so that the AI Study Assistant and learners can reference trusted material.
18. As a learner, I want to search Course Resources, Approved FAQs, Channel Summaries, and relevant messages, so that I can find answers quickly.
19. As a learner, I want to see Course Resources I am allowed to access, so that enrollment boundaries are respected.

### AI Study Assistant

20. As an Instructor, I want one AI Study Assistant installed in my Study Server with explicit grants per channel, Course, Cohort, and Course Resource, so that the assistant is powerful but bounded.
21. As a learner, I want the AI Study Assistant to answer from approved materials in the channel I am in, so that it feels like a course-specific helper.
22. As a learner, I want the AI Study Assistant to cite sources, so that I can trust and verify answers.
23. As a learner, I want the assistant to say when it does not know, so that I do not rely on hallucinated course guidance.
24. As an Instructor, I want to control which channels the AI Study Assistant can read, so that private channels stay protected.
25. As a learner, I want to see when the AI Study Assistant is present in a channel, so that I know my messages may be used for context.
26. As a Study Server Owner, I want audit logs for assistant answers and resource access, so that AI behavior is reviewable.

### Support Questions, FAQ, TA Queue, And Office Hours

27. As a learner, I want to ask a Support Question in a Course Channel, so that I can get help without leaving the community.
28. As a learner, I want similar Support Questions detected, so that I can find previous explanations before asking again.
29. As an Instructor, I want repeated Support Questions to become FAQ candidates, so that I can reduce repetitive work.
30. As an Instructor, I want to approve or edit Approved FAQs, so that official course knowledge stays accurate.
31. As a TA, I want to see unanswered or low-confidence Support Questions in the TA Queue, so that I can prioritize human help.
32. As a learner, I want low-confidence AI answers routed to the TA Queue, so that I still get human follow-up.
33. As a learner, I want to join Office Hours during the scheduled window for my Cohort, so that I can get live support.
34. As a TA, I want to manage the TA Queue and Office Hours for my Cohort, so that support is fair and organized.

### Instructor Dashboard And Summaries

35. As an Instructor, I want an Instructor Dashboard with unanswered questions, repeated questions, misconceptions, engagement, Office Hours load, and AI usage, so that I can manage the course without reading every message.
36. As an Instructor, I want Channel Summaries for Course Channels, so that I can quickly understand what happened each week.
37. As a learner, I want summarized study notes from active discussions, so that I can review important concepts later.
38. As a Study Server Owner, I want to see AI usage against my SaaS Plan, so that I can manage cost and quotas.

### Billing

39. As a Study Server Owner, I want to subscribe to a SaaS Plan for my Study Server, so that community, AI, and operations are bundled.
40. As a Study Server Owner, I want clear behavior when AI quotas are exhausted, so that I am not surprised by blocked assistant use.

### Later Phase: Creator Course Commerce

41. As an Instructor in a later phase, I want to sell a Course inside my Study Server, so that learners can enroll, learn, and ask questions in one place.
42. As a learner in a later phase, I want Cohort Enrollment from a purchase to unlock the right channels, resources, Live Classes, and AI Study Assistant access, so that enrollment feels seamless.
43. As an Instructor in a later phase, I want to host a built-in Live Class inside Chanter, so that teaching, chat, Q&A, recordings, and summaries stay connected to the community.

## Implementation Decisions

- The first product surface is an education-focused Study Server, not a generic public Discord clone.
- The first buyer is educators running cohort-based courses, bootcamps, tutoring groups, and study communities.
- The first business model is SaaS subscription tiers paid by the Study Server Owner, with AI usage quotas and plan limits.
- Users have global accounts. Roles are layered: Study Server Owner for governance; Instructor at Course scope; TA at Cohort scope; Learner through Cohort Enrollment.
- A user can be an Instructor for one Course, a TA for one Cohort, and a Learner in another Cohort or Course in the same Study Server.
- Enrollment is primarily to a Cohort. Course channels and shared Course Resources follow from Cohort membership.
- Study Server membership requires invite or Enrollment in at least one Cohort.
- Channels: Study Server Channels, Course Channels, optional Cohort Channels, and Voice Channels in Study Server.
- Direct Messages and Friend Requests are in the MVP with Discord-like accept flow and durable REST messaging (#15). The full Discord-like **Friends Hub** (friends list, online presence, live DM conversation panel) and **DM voice calls** are post-MVP slices #31 and #32 after `realtime-service` and WebRTC/LiveKit transport land. See `docs/architecture/social-hub-and-dm-voice.md`. Channels, TA Queue, and Office Hours remain the primary learning-support paths.
- **Social vs learning visibility:** Friends and DMs are **platform-wide** (global friend graph). Course channels, resources, Support Questions, and search results are **enrollment- and role-scoped** — learners see only **their courses** in the Study Server sidebar, not every course on the server. Friend requests should require **co-membership** in at least one Study Server before #31. See `docs/product-design/visibility-and-social-model.md`.
- Organization is post-MVP. Verified Educator is a post-MVP profile badge that does not grant permissions.
- The first AI capability is a first-party AI Study Assistant installed once per Study Server with explicit grants per channel, Course, Cohort, and Course Resource.
- Support Questions move through AI-answered, human-answered, unanswered, duplicate, and FAQ-candidate states.
- Approved FAQs and Course Resources power grounded answers, search, and Channel Summaries.
- TA Queue handles async human help anytime; Office Hours is the scheduled live window for a Cohort.
- Built-in Live Class video is post-MVP. Voice Channels are in MVP; full live teaching video is not.
- Instructor Dashboard aggregates come from event-driven read models, not cross-service database reads.
- Service boundaries: Community Service for Study Servers, Courses, Cohorts, channels, enrollment, and permissions; User Service for profiles, friends, blocks, and DMs; Message Service for chat and Support Questions; Realtime Service for WebSocket and voice signaling; Media Service for Course Resources; Search Service for retrieval; Agent Service and Agent Runtime Service for the AI Study Assistant; Analytics Service for Instructor Dashboard; Billing Service for SaaS Plans and quotas; Safety Service for policy checks.
- Marketplace, third-party agents, voice agents, and creator course commerce remain later phases.

## Testing Decisions

- Tests should verify external behavior and user outcomes, not implementation details.
- Permission tests are critical: the AI Study Assistant must not access channels, resources, or messages outside its grants.
- Enrollment tests must verify Cohort-scoped access to Course Channels, Cohort Channels, and Course Resources.
- Role tests must verify Course-scoped Instructor powers, Cohort-scoped TA powers, and cross-Course role combinations in one Study Server.
- Support Question workflow tests should cover unanswered, answered, duplicate, FAQ candidate, and human handoff states.
- Resource-grounded answer tests should verify answers are constrained to approved resources and low-confidence responses route safely.
- TA Queue and Office Hours tests should cover join, leave, next-learner assignment, TA permissions, and auditability.
- Voice Channel tests should cover join, leave, permissions, and presence visibility.
- Direct Message tests should cover friend request accept/decline, block, and unauthorized messaging.
- Friends Hub tests (#31) should cover friends list scoping, presence fan-out, and live DM delivery over WebSocket.
- DM voice tests (#32) should cover friendship/block gates, call accept/decline, and short-lived media token scoping.
- Analytics tests should verify event-driven Instructor Dashboard aggregates.
- Billing tests should verify SaaS Plan limits, AI metering, and graceful quota exhaustion.
- E2E tests should cover: learner enrolls in a Cohort, asks a Support Question, receives a grounded assistant answer, joins TA Queue or Office Hours, and Instructor reviews the Instructor Dashboard.
- Docker Compose smoke tests should eventually verify the Study Server path across frontend, gateway, core services, event broker, storage, voice signaling, and AI stubs.

## Out of Scope

- Public marketplace for third-party agents.
- Course storefronts, learner payments, refunds, tax handling, creator payouts, and platform take-rate.
- Built-in Live Class video rooms, recordings, and transcripts.
- Voice agents participating in voice rooms.
- Full LMS replacement: graded assignments, gradebooks, SCORM import, accreditation workflows, transcript issuance.
- Organization workspaces, enterprise SSO, SCIM, roster import, and domain enforcement in the first MVP.
- Verified Educator badge and institution verification in the first MVP.
- Native mobile applications.
- Large-scale multi-region/cell deployment beyond preserving planned service boundaries.

## Further Notes

The MVP should win by reducing educator workload and making learning communities easier to run. The strongest first demo is: a learner enrolled in a Cohort asks a repeated Support Question, the AI Study Assistant answers from approved Course Resources, the learner gets routed to the TA Queue when confidence is low, and the Instructor promotes the explanation to an Approved FAQ on the Instructor Dashboard.

Learners should still feel the familiar Discord-like surface: channels, voice, friends, and DMs. Issue #15 establishes friend/DM backend rules; issues #31–#32 deliver the full social UX (friends list with presence, live DM chat box, friend voice calls). The buyer value is learning operations: fewer repeated questions, faster support, better knowledge retention, and lower instructor/TA overhead.

Domain language is canonical in `CONTEXT.md`. Grill session decisions are logged in `docs/sessions/2026-06-16-product-strategy-grill-session.md`.
