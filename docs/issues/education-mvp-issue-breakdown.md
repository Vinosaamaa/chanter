# Chanter Education MVP Issue Breakdown

> **Product visuals:** target UI mockups and screen flows for each slice live in [`docs/product-design/`](../product-design/README.md) (see `mockups/README.md` for the gallery).  
> **Visibility:** global friends + enrollment-scoped **My courses** sidebar — [`visibility-and-social-model.md`](../product-design/visibility-and-social-model.md).

## GitHub Issues Published

Milestone: [Education MVP](https://github.com/Vinosaamaa/chanter/milestone/1)

Epics (#1–#10):

| # | Title |
|---|---|
| 1 | Epic: Education Product Foundation |
| 2 | Epic: Study Server, Course, Cohort, And Enrollment |
| 3 | Epic: Channels, Voice, And Study Server Membership |
| 4 | Epic: Friends And Direct Messages |
| 5 | Epic: Course Resources And Search |
| 6 | Epic: AI Study Assistant MVP |
| 7 | Epic: Support Questions, Approved FAQs, And Channel Summaries |
| 8 | Epic: TA Queue And Office Hours |
| 9 | Epic: Instructor Dashboard And Learning Analytics |
| 10 | Epic: SaaS Plans, Quotas, And Billing Readiness |

Vertical slices (#11–#24):

| # | Title | Start when unblocked |
|---|---|---|
| 11 | Monorepo And Local Infrastructure Bootstrap | Done |
| 12 | Slice: Create A Study Server | Done |
| 13 | Slice: Create Course, Cohort, And Enroll Learner | Done |
| 14 | Slice: Join A Voice Channel | Done |
| 15 | Slice: Send Friend Request And Direct Message | Done |
| 16 | Slice: Post A Support Question In A Course Channel | Done |
| 17 | Slice: Upload An Approved Course Resource | **Active** |
| 18 | Slice: Install The AI Study Assistant | after #13, #17 (HITL) |
| 19 | Slice: Answer A Grounded Support Question | after #18 |
| 20 | Slice: Promote Repeated Support Question To Approved FAQ | after #19 |
| 21 | Slice: Route Low-Confidence Answer To TA Queue | after #19 |
| 22 | Slice: Run Office Hours For A Cohort | after #13, #21 |
| 23 | Slice: Show Instructor Dashboard | after #20, #21 |
| 24 | Slice: Enforce SaaS Plan Limits | after #23 |

Cross-cutting (post-MVP vertical slices):

| # | Title | Notes |
|---|---|---|
| 30 | Slice: Wire Auth Service Principal Into Protected Endpoints | Replaces `TODO(#auth)` on #12–#14; see `docs/operations/issue-14-greptile-fix.md` |

Post-MVP social & realtime (milestone: [Social Hub & Realtime](https://github.com/Vinosaamaa/chanter/milestone/2), project: [Social Hub & Realtime (Post-MVP)](https://github.com/users/Vinosaamaa/projects/2)):

| # | Title | Start when unblocked |
|---|---|---|
| 31 | Slice: Build Discord-Like Friends Hub And Live DM Conversation | after #15, #30; requires `realtime-service` bootstrap |
| 32 | Slice: Direct Message Voice Call Between Friends | after #31; requires Voice Channel WebRTC/LiveKit transport |

Architecture: `docs/architecture/social-hub-and-dm-voice.md`

All issues: https://github.com/Vinosaamaa/chanter/issues

Project board: [Chanter Education MVP](https://github.com/users/Vinosaamaa/projects/1) — issues #1–#24 and #30.

Post-MVP project: [Social Hub & Realtime (Post-MVP)](https://github.com/users/Vinosaamaa/projects/2) — issues #31–#32 (milestone 2).

Recommended labels:

- `epic`
- `story`
- `ready-for-agent`
- `education`
- `ai-agent`
- `backend`
- `frontend`
- `infra`
- `realtime`
- `security`
- `billing`
- `analytics`

## Epic 1: Education Product Foundation

## What to build

Establish Chanter's education-first Study Server SaaS wedge. Align implementation milestones with `CONTEXT.md` and the PRD demo path.

## Acceptance criteria

- [ ] Product docs use canonical domain language from `CONTEXT.md`.
- [ ] First milestones prioritize Study Server, Course/Cohort Enrollment, channels, voice, DMs, AI Study Assistant, Support Questions, TA Queue, Office Hours, and Instructor Dashboard.
- [ ] Non-goals are explicit: built-in Live Class video, marketplace, voice agents, Organization SSO, and creator commerce are later phases.

## Blocked by

None - can start immediately.

## Epic 2: Study Server, Course, Cohort, And Enrollment

## What to build

Let a Study Server Owner create a Study Server, add Courses and Cohorts, enroll learners in a Cohort, and assign layered roles: Study Server Owner, Course Instructor, Cohort TA, and Cohort Learner.

## Acceptance criteria

- [ ] Study Server Owner can create a Study Server and become Owner.
- [ ] Owner can create Courses and Cohorts inside the Study Server.
- [ ] Owner can assign Instructors to Courses.
- [ ] Instructor can enroll learners in a Cohort and assign Cohort TAs.
- [ ] Enrollment grants Cohort and parent Course access only—not blanket Study Server access beyond membership rules.
- [ ] Same user can hold different roles across Courses/Cohorts in one Study Server.
- [ ] Tests cover enrollment boundaries and cross-role combinations.

## Blocked by

Project operations bootstrap and monorepo/service skeleton.

## Epic 3: Channels, Voice, And Study Server Membership

## What to build

Support Study Server Channels, Course Channels, optional Cohort Channels, Voice Channels, and Study Server membership via invite or Cohort Enrollment.

## Acceptance criteria

- [ ] Study Server Owner/Instructor can create Study Server Channels, Course Channels, and opt-in Cohort Channels.
- [ ] Study Server Members can access server-wide channels per membership rules.
- [ ] Enrolled learners can access permitted Course and Cohort channels.
- [ ] Study Server sidebar shows **My courses** filtered by enrollment and role — learners do not see the full server course catalog.
- [ ] Members can join and leave Voice Channels with visible presence.
- [ ] Backend permission checks protect channel administration and access.
- [ ] Tests cover channel scoping, enrollment-based access, and voice join permissions.

## Blocked by

Study Server, Course, Cohort, And Enrollment.

## Epic 4: Friends And Direct Messages

## What to build

Discord-style social messaging: Friend Requests, accept/decline, Direct Messages, and block/report basics.

## Acceptance criteria

- [ ] User can send, accept, and decline Friend Requests.
- [ ] Friends can exchange Direct Messages platform-wide (REST persistence in #15).
- [ ] Friend requests are limited to users who share at least one Study Server membership (co-membership); see `docs/product-design/visibility-and-social-model.md`.
- [ ] User can block another user and stop receiving messages.
- [ ] DM and friend flows are separate from Course Channel learning-support workflows.
- [ ] Tests cover accept/decline, unauthorized messaging, and block behavior.
- [ ] Discord-like Friends Hub, live DM delivery, and DM voice are tracked post-MVP in #31 and #32 (see `docs/architecture/social-hub-and-dm-voice.md`).

## Blocked by

Project operations bootstrap and monorepo/service skeleton.

## Epic 5: Course Resources And Search

## What to build

Let Instructors attach Course Resources to a Course and make them searchable for permitted learners and future AI grounding.

## Acceptance criteria

- [ ] Instructor can upload or register a Course Resource for a Course.
- [ ] Cohort-specific resources can be attached where needed.
- [ ] Learners see only resources their Enrollment allows.
- [ ] System records which resources are approved for AI Study Assistant use.
- [ ] Search respects enrollment and role permissions.
- [ ] Tests verify unauthorized resource and search access denial.

## Blocked by

Study Server, Course, Cohort, And Enrollment.

## Epic 6: AI Study Assistant MVP

## What to build

Install one AI Study Assistant per Study Server with explicit grants per channel, Course, Cohort, and Course Resource. Answer Support Questions with grounded, auditable responses.

## Acceptance criteria

- [ ] Instructor can install the assistant and configure channel/Course/Cohort/resource grants.
- [ ] Learner can ask a Support Question in a granted channel.
- [ ] Assistant answers from approved Course Resources and Approved FAQs or declines with low-confidence handoff.
- [ ] Assistant presence and grants are visible to users.
- [ ] Audit records capture invocation, sources used, confidence, and safety decisions.
- [ ] Tests cover grant boundaries, grounded success, low-confidence routing, and audit logging.

## Blocked by

Channels, Voice, And Study Server Membership; Course Resources And Search.

## Epic 7: Support Questions, Approved FAQs, And Channel Summaries

## What to build

Track Support Questions through AI-answered, human-answered, unanswered, duplicate, and FAQ-candidate states. Let Instructors approve FAQs and generate Channel Summaries.

## Acceptance criteria

- [ ] Support Questions are created from learner messages in Course Channels.
- [ ] Similar Support Questions are detected and surfaced.
- [ ] Instructor can approve or edit Approved FAQs.
- [ ] Approved FAQs are searchable and available to the AI Study Assistant.
- [ ] Channel Summaries can be generated for Course Channels over a time window.
- [ ] Tests cover workflow states, FAQ approval permissions, and summary access boundaries.

## Blocked by

AI Study Assistant MVP.

## Epic 8: TA Queue And Office Hours

## What to build

Async TA Queue for a Cohort plus scheduled Office Hours live support, including handoff from low-confidence AI answers.

## Acceptance criteria

- [ ] Learner can add a Support Question to the TA Queue for their Cohort.
- [ ] TA can view, pick up, resolve, and cancel queue items.
- [ ] Instructor can schedule Office Hours for a Cohort.
- [ ] Learner can join Office Hours during the scheduled window.
- [ ] Low-confidence AI responses offer TA Queue and Office Hours paths.
- [ ] Queue and Office Hours events are auditable.
- [ ] Tests cover queue order, Cohort scoping, Office Hours window rules, and permissions.

## Blocked by

Support Questions, Approved FAQs, And Channel Summaries.

## Epic 9: Instructor Dashboard And Learning Analytics

## What to build

Instructor Dashboard for unanswered Support Questions, repeated questions, Approved FAQs, TA Queue load, Office Hours load, engagement signals, and AI usage.

## Acceptance criteria

- [ ] Instructor or Study Server Owner can view the dashboard for permitted Courses/Study Servers.
- [ ] Dashboard shows unanswered questions, repeated questions, FAQ candidates, queue load, Office Hours load, and AI usage.
- [ ] Aggregates are built from event-driven read models.
- [ ] Tests verify aggregate correctness and unauthorized access denial.

## Blocked by

Support Questions, Approved FAQs, And Channel Summaries; TA Queue And Office Hours.

## Epic 10: SaaS Plans, Quotas, And Billing Readiness

## What to build

SaaS Plans paid by Study Server Owner: Starter, Pro, and Organization-ready tiers with AI metering and quota exhaustion behavior.

## Acceptance criteria

- [ ] Study Server has an associated SaaS Plan tier paid by the Study Server Owner.
- [ ] AI usage is metered per Study Server.
- [ ] Plan limits control AI usage, resource capacity, and advanced dashboard access.
- [ ] Quota exhaustion is visible on the Instructor Dashboard and fails gracefully.
- [ ] Tests cover plan enforcement, metering, and quota-denied assistant calls.

## Blocked by

AI Study Assistant MVP; Instructor Dashboard And Learning Analytics.

---

## Vertical Slices

### Vertical Slice 1: Create A Study Server

Type: AFK

## What to build

Smallest end-to-end path: Study Server Owner creates a Study Server and lands in the server shell with default Study Server Channels.

## Acceptance criteria

- [ ] User can create a Study Server from the frontend.
- [ ] Backend persists the Study Server and Owner role.
- [ ] Default Study Server Channels are created.
- [ ] Smoke test verifies create-and-view path.

## Blocked by

Project operations bootstrap and monorepo/service skeleton.

### Vertical Slice 2: Create Course, Cohort, And Enroll Learner

Type: AFK

## What to build

Owner creates a Course and Cohort; Instructor enrolls a learner in the Cohort; learner gains access to Course Channels only through Enrollment.

## Acceptance criteria

- [x] Owner can create a Course and Cohort.
- [x] Instructor can enroll a learner in the Cohort.
- [x] Enrolled learner can access Course Channels; non-enrolled user cannot.
- [x] Tests cover enrollment boundaries.

## Blocked by

Create A Study Server.

### Vertical Slice 3: Join A Voice Channel

Type: AFK

## What to build

Study Server Member can join and leave a Voice Channel with visible presence.

## Acceptance criteria

- [ ] Study Server has at least one Voice Channel.
- [ ] Member can join, speak/listen, and leave the Voice Channel.
- [ ] Non-members cannot join.
- [ ] Tests cover join permissions and presence updates.

## Blocked by

Create A Study Server.

### Vertical Slice 4: Send Friend Request And Direct Message

Type: AFK

## What to build

User sends Friend Request; recipient accepts; users exchange Direct Messages.

## Acceptance criteria

- [ ] User can send and accept Friend Requests.
- [ ] Friends can send Direct Messages.
- [ ] Non-friends cannot DM without acceptance.
- [ ] Tests cover accept/decline and block behavior.

## Blocked by

Project operations bootstrap and monorepo/service skeleton.

### Vertical Slice 5: Post A Support Question In A Course Channel

Type: AFK

## What to build

Enrolled learner posts a Support Question in a Course Channel; system tracks it as unanswered.

## Acceptance criteria

- [ ] Learner can post in permitted Course Channel.
- [ ] Message is durable and idempotent.
- [ ] Support Question workflow record/event is created.
- [ ] Instructor/TA can see unanswered Support Questions.
- [ ] Tests cover unauthorized posting.

## Blocked by

Create Course, Cohort, And Enroll Learner.

### Vertical Slice 6: Upload An Approved Course Resource

Type: AFK

## What to build

Instructor attaches a Course Resource approved for AI use.

## Acceptance criteria

- [ ] Instructor can upload/register a Course Resource on a Course.
- [ ] Resource metadata records Course scope and AI-approved status.
- [ ] Enrolled learner can view permitted resources.
- [ ] Unauthorized users and agents cannot access private resources.

## Blocked by

Create Course, Cohort, And Enroll Learner.

### Vertical Slice 7: Install The AI Study Assistant

Type: HITL

## What to build

First install flow for the AI Study Assistant with visible grants for channels, Courses, Cohorts, and Course Resources.

## Acceptance criteria

- [ ] Instructor reviews and confirms assistant grants before install.
- [ ] Users see assistant presence and allowed scope.
- [ ] Backend stores install and grant records.
- [ ] Tests cover grant boundaries.

## Blocked by

Create Course, Cohort, And Enroll Learner; Upload An Approved Course Resource.

### Vertical Slice 8: Answer A Grounded Support Question

Type: AFK

## What to build

Learner asks the AI Study Assistant; assistant returns grounded answer or low-confidence handoff.

## Acceptance criteria

- [ ] Learner invokes assistant in granted Course Channel.
- [ ] Assistant uses only approved resources/FAQs and permitted context.
- [ ] Response is persisted; audit record created.
- [ ] Low-confidence path routes to human help.
- [ ] Tests cover success, denial, and handoff.

## Blocked by

Install The AI Study Assistant.

### Vertical Slice 9: Promote Repeated Support Question To Approved FAQ

Type: AFK

## What to build

Detect repeated Support Questions; Instructor approves Approved FAQ for search and assistant use.

## Acceptance criteria

- [ ] Similar Support Questions are grouped.
- [ ] Instructor can approve/edit Approved FAQ.
- [ ] Approved FAQ is searchable and assistant-accessible.
- [ ] Tests cover permissions and retrieval.

## Blocked by

Answer A Grounded Support Question.

### Vertical Slice 10: Route Low-Confidence Answer To TA Queue

Type: AFK

## What to build

Low-confidence assistant response lets learner add Support Question to Cohort TA Queue; TA resolves it.

## Acceptance criteria

- [ ] Learner can add question to TA Queue from handoff UI.
- [ ] Cohort TA can pick up and resolve queue items.
- [ ] Queue activity is auditable.
- [ ] Tests cover Cohort scoping and permissions.

## Blocked by

Answer A Grounded Support Question.

### Vertical Slice 11: Run Office Hours For A Cohort

Type: AFK

## What to build

Instructor schedules Office Hours; enrolled learners join during the window; TA manages live support flow.

## Acceptance criteria

- [ ] Instructor can schedule Office Hours for a Cohort.
- [ ] Enrolled learner can join only during the window.
- [ ] TA can run the live support session.
- [ ] Tests cover schedule boundaries and enrollment checks.

## Blocked by

Create Course, Cohort, And Enroll Learner; Route Low-Confidence Answer To TA Queue.

### Vertical Slice 12: Show Instructor Dashboard

Type: AFK

## What to build

First Instructor Dashboard with unanswered/repeated Support Questions, Approved FAQs, queue load, Office Hours load, and AI usage.

## Acceptance criteria

- [ ] Instructor views dashboard for permitted Study Server/Course.
- [ ] Aggregates come from events/read models.
- [ ] Unauthorized users are denied.
- [ ] Tests cover aggregate correctness.

## Blocked by

Promote Repeated Support Question To Approved FAQ; Route Low-Confidence Answer To TA Queue.

### Vertical Slice 13: Enforce SaaS Plan Limits

Type: AFK

## What to build

Study Server Owner's SaaS Plan limits AI usage and surfaces quota exhaustion.

## Acceptance criteria

- [ ] Study Server has plan tier.
- [ ] AI usage meters against plan.
- [ ] Quota exhaustion blocks/degrades assistant with clear messaging.
- [ ] Tests cover metering and enforcement.

## Blocked by

Show Instructor Dashboard.
