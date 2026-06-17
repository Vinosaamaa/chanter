# Chanter Education MVP Issue Breakdown

This document converts `docs/product/education-mvp-prd.md` into GitHub-ready epics and vertical-slice stories. It is a local issue source until the GitHub repository and GitHub Projects board exist.

Recommended labels:

- `epic`
- `story`
- `ready-for-agent`
- `education`
- `ai-agent`
- `backend`
- `frontend`
- `infra`
- `security`
- `billing`
- `analytics`

## Epic 1: Education Product Foundation

## What to build

Establish Chanter's first product wedge as an education-focused Study Server SaaS product. This epic creates the product language, local demo expectations, and first vertical slices that make Chanter more than a generic Discord clone.

## Acceptance criteria

- [ ] Product docs consistently describe Chanter as education-first for the initial MVP.
- [ ] The first implementation milestones prioritize Study Server creation, course channels, learner/instructor roles, and AI Study Assistant readiness.
- [ ] Non-goals are explicit: marketplace, voice agents, enterprise SSO/compliance, and full LMS replacement are later phases.

## Blocked by

None - can start immediately.

## Epic 2: Study Server And Course Channels

## What to build

Let an educator create a Study Server with course/module channels, learner/instructor/TA roles, and permissions that can later constrain messages, resources, and AI assistant access.

## Acceptance criteria

- [ ] An educator can create a Study Server and see it in the frontend.
- [ ] An educator can create channels for modules, general discussion, announcements, and support.
- [ ] The system supports instructor, TA, learner, and admin roles.
- [ ] Backend permission checks protect Study Server and channel management actions.
- [ ] Unit/integration tests cover role assignment and protected channel administration.

## Blocked by

Project operations bootstrap and monorepo/service skeleton.

## Epic 3: Course Resources And Knowledge Base

## What to build

Allow instructors to attach approved course resources to a Study Server or channel, then make those resources searchable and available to the AI Study Assistant through explicit grants.

## Acceptance criteria

- [ ] An instructor can upload or register a course resource for a Study Server/channel.
- [ ] Learners can view resources that they are allowed to access.
- [ ] The system records which resources are approved for AI use.
- [ ] Search/retrieval boundaries respect channel and role permissions.
- [ ] Tests verify that private resources are not returned to unauthorized learners or agents.

## Blocked by

Study Server And Course Channels.

## Epic 4: AI Study Assistant MVP

## What to build

Install a first-party AI Study Assistant into selected Study Server channels. The assistant answers learner questions using approved resources and visible channel context, while remaining permissioned, auditable, and bounded by safety rules.

## Acceptance criteria

- [ ] An instructor can enable the AI Study Assistant in selected channels.
- [ ] Learners can mention or ask the assistant a course question.
- [ ] The assistant answers from approved resources or recent allowed context.
- [ ] Low-confidence or out-of-scope questions are declined or routed to human help.
- [ ] Assistant presence and channel access are visible to users.
- [ ] Audit records capture assistant invocation, resources used, response state, and safety decisions.
- [ ] Tests cover permission denial, grounded answer success, low-confidence handling, and audit logging.

## Blocked by

Study Server And Course Channels; Course Resources And Knowledge Base.

## Epic 5: Question Workflow And FAQ Generation

## What to build

Turn chat questions into a managed learning-support workflow. Detect repeated questions, unresolved questions, human answers, and FAQ candidates that instructors can approve.

## Acceptance criteria

- [ ] Questions can be marked as answered, unanswered, duplicate, or FAQ candidate.
- [ ] Similar questions are suggested before or after a learner asks.
- [ ] Instructors/TAs can approve or edit generated FAQ entries.
- [ ] Approved FAQ entries are searchable and available to the AI Study Assistant.
- [ ] Tests cover repeated-question detection, FAQ approval, and unauthorized FAQ edits.

## Blocked by

AI Study Assistant MVP.

## Epic 6: Office-Hours Queue And TA Handoff

## What to build

Give learners a structured way to request live support and give TAs/instructors a manageable queue for office hours, including handoff from AI to human help.

## Acceptance criteria

- [ ] A learner can join and leave an office-hours queue.
- [ ] A TA/instructor can call the next learner and mark the support request as resolved.
- [ ] AI-declined or low-confidence questions can become queue items.
- [ ] Queue events are auditable.
- [ ] Tests cover queue order, permissions, cancellation, and resolution.

## Blocked by

Study Server And Course Channels; Question Workflow And FAQ Generation.

## Epic 7: Instructor Dashboard And Learning Analytics

## What to build

Create an instructor dashboard that summarizes unanswered questions, repeated questions, common misconceptions, active topics, engagement trends, AI usage, and office-hours load.

## Acceptance criteria

- [ ] Instructors can view unanswered and repeated questions by Study Server/channel.
- [ ] Instructors can view AI Study Assistant usage and quota/cost indicators.
- [ ] Instructors can see office-hours queue volume and resolution trends.
- [ ] Analytics are built through event-driven read models, not cross-service database reads.
- [ ] Tests verify aggregate correctness from representative events.

## Blocked by

Question Workflow And FAQ Generation; Office-Hours Queue And TA Handoff.

## Epic 8: SaaS Plans, Quotas, And Billing Readiness

## What to build

Add plan-aware limits for education SaaS packaging: Starter, Pro, and Organization. Track AI usage, Study Server limits, resource limits, and quota exhaustion behavior.

## Acceptance criteria

- [ ] Study Servers have an associated plan tier.
- [ ] AI usage is metered per Study Server.
- [ ] Plan limits control AI usage, resource capacity, and advanced analytics access.
- [ ] Quota exhaustion is visible to instructors and fails gracefully.
- [ ] Tests cover plan enforcement, metering, and quota-denied assistant calls.

## Blocked by

AI Study Assistant MVP; Instructor Dashboard And Learning Analytics.

## Vertical Slice 1: Create A Study Server

Type: AFK

## What to build

Create the smallest end-to-end path where an educator can create a Study Server, land in the server shell, and see default channels and roles.

## Acceptance criteria

- [ ] Educator can create a Study Server from the frontend.
- [ ] Backend persists the Study Server through the owning service.
- [ ] Default channels and roles are created.
- [ ] Protected actions require instructor/admin permissions.
- [ ] A smoke test verifies the create-and-view path.

## Blocked by

Project operations bootstrap and monorepo/service skeleton.

## Vertical Slice 2: Ask A Question In A Course Channel

Type: AFK

## What to build

Let a learner post a question in a course channel and let the system classify it as a question that can later be answered, deduplicated, or surfaced to instructors.

## Acceptance criteria

- [ ] Learner can post a message in an allowed course channel.
- [ ] Message creation is durable and idempotent.
- [ ] Question-like messages create a question workflow record or event.
- [ ] Instructor/TA can see unanswered questions.
- [ ] Tests cover unauthorized channel posting and duplicate send protection.

## Blocked by

Create A Study Server.

## Vertical Slice 3: Upload An Approved Course Resource

Type: AFK

## What to build

Let an instructor attach an approved resource to a course channel and make it available for learner search and future AI grounding.

## Acceptance criteria

- [ ] Instructor can upload or register a resource.
- [ ] Resource metadata records Study Server, channel scope, owner, and AI-approved status.
- [ ] Learner can see resources they are permitted to access.
- [ ] Unauthorized users and agents cannot access private resources.
- [ ] Tests cover upload authorization and scoped resource access.

## Blocked by

Create A Study Server.

## Vertical Slice 4: Install The AI Study Assistant

Type: HITL

## What to build

Define and implement the first install flow for the first-party AI Study Assistant, including visible permissions, channel bindings, resource access, and admin approval.

## Acceptance criteria

- [ ] Instructor can review requested assistant permissions before install.
- [ ] Instructor can select allowed channels and approved resources.
- [ ] Users can see that the assistant is present and what it can access.
- [ ] Backend stores channel bindings and permission grants.
- [ ] Tests cover install approval, denial, and channel/resource access boundaries.

## Blocked by

Create A Study Server; Upload An Approved Course Resource.

## Vertical Slice 5: Answer A Grounded Course Question

Type: AFK

## What to build

Let a learner ask the AI Study Assistant a question and receive an answer grounded in approved resources or allowed context.

## Acceptance criteria

- [ ] Learner can invoke the assistant in an enabled channel.
- [ ] Assistant retrieves only approved resources and permitted context.
- [ ] Assistant returns an answer or a clear low-confidence/handoff response.
- [ ] Final assistant response is persisted as a normal durable message.
- [ ] Audit records capture invocation, resource scope, safety state, and usage.
- [ ] Tests cover successful answer, denied resource access, low-confidence response, and durable persistence.

## Blocked by

Install The AI Study Assistant.

## Vertical Slice 6: Promote Repeated Questions Into FAQ

Type: AFK

## What to build

Detect repeated learner questions, suggest an FAQ entry, and let an instructor approve it for future search and assistant use.

## Acceptance criteria

- [ ] Similar questions are grouped as repeated-question candidates.
- [ ] Instructor can review, edit, and approve an FAQ entry.
- [ ] Approved FAQ entries become searchable and assistant-accessible.
- [ ] Learners can discover the approved FAQ before asking again.
- [ ] Tests cover duplicate grouping, approval permissions, and FAQ retrieval.

## Blocked by

Answer A Grounded Course Question.

## Vertical Slice 7: Route Low-Confidence Questions To Office Hours

Type: AFK

## What to build

When the assistant cannot answer confidently, let the learner add the question to an office-hours queue for TA/instructor support.

## Acceptance criteria

- [ ] Low-confidence assistant responses offer a human-help path.
- [ ] Learner can add the question to the office-hours queue.
- [ ] TA/instructor can call, resolve, or cancel queue items.
- [ ] Queue activity is visible and auditable.
- [ ] Tests cover queue transitions, permissions, and duplicate queue entries.

## Blocked by

Answer A Grounded Course Question.

## Vertical Slice 8: Show Instructor Insights

Type: AFK

## What to build

Create the first instructor dashboard that shows actionable course operations: unanswered questions, repeated questions, approved FAQs, office-hours load, and AI usage.

## Acceptance criteria

- [ ] Instructor can view the dashboard for a Study Server.
- [ ] Dashboard includes unanswered questions, repeated questions, FAQ candidates, office-hours load, and AI usage.
- [ ] Aggregates are built from events/read models.
- [ ] Dashboard enforces instructor/admin access.
- [ ] Tests cover aggregate correctness and unauthorized access denial.

## Blocked by

Promote Repeated Questions Into FAQ; Route Low-Confidence Questions To Office Hours.

## Vertical Slice 9: Enforce SaaS Plan Limits

Type: AFK

## What to build

Add Starter, Pro, and Organization plan limits for AI usage, resource capacity, Study Server scale, and advanced dashboard access.

## Acceptance criteria

- [ ] Study Server has a plan tier.
- [ ] AI usage is metered against the plan.
- [ ] Quota exhaustion blocks or degrades assistant use with clear instructor-facing messaging.
- [ ] Plan-gated dashboard/resource capabilities are enforced on the backend.
- [ ] Tests cover metering, plan limit checks, and user-visible quota behavior.

## Blocked by

Show Instructor Insights.

