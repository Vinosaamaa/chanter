# Chanter Learning Community Context

This glossary defines the core product language for Chanter's education-first learning community platform. It exists to keep product, design, and implementation discussions consistent.

## Language

**Study Server**:
A community container for a learning group, cohort, course business, or organization. It contains members, roles, channels, permissions, resources, support workflows, and learning context.
_Avoid_: Server, workspace, classroom

**Course**:
A structured learning product inside a Study Server. A Course may later be sellable or enrollable and can grant access to channels, resources, live classes, and AI Study Assistant capabilities.
_Avoid_: Class, program, module

**Cohort**:
A scheduled group of learners taking a Course together. A Course can have many Cohorts, each with its own schedule, live classes, enrollment window, support workflow, and learner membership.
_Avoid_: Class, session, term, course run

**Enrollment**:
A learner's participation in a Cohort. Enrollment grants access to that Cohort's spaces and the parent Course's shared channels and resources—not blanket access to the whole Study Server.
_Avoid_: Purchase, membership, subscription

**Live Class**:
A scheduled live teaching session for a Cohort. Built-in video is post-MVP; the term covers the scheduled session and its follow-up materials such as recordings, transcripts, and summaries once live teaching ships.
_Avoid_: Voice room, video call, meeting

**Course Resource**:
Learning material attached to a Course by default. A Cohort can also have Cohort-specific resources such as schedules, recordings, transcripts, or temporary materials for that group.
_Avoid_: File, attachment, document

**AI Study Assistant**:
A first-party learning helper installed once in a Study Server. It answers only from explicitly approved channels, Courses, Cohorts, and Course Resources. In each channel it may feel like a different helper because its allowed context, grounding, and scope change with those grants—not because there are separate assistants. When it is not confident, it should say so and route the question to human support rather than guess.
_Avoid_: Bot, chatbot, generic agent

**Office Hours**:
A scheduled live support window for a Cohort. Enrolled learners can join during the window to get human help. Questions routed from the AI Study Assistant when it is not confident can enter the same support flow.
_Avoid_: Help desk, support ticket, meeting

**Study Server Owner**:
A Study Server-scoped governance role. The Owner creates and governs the Study Server, can create Courses, and can assign Instructors. Owner is about running the community—not about being the teacher in every Course.
_Avoid_: Admin, workspace owner

**Instructor**:
A Course-scoped teaching role. An Instructor manages a Course's content, Course Resources, live classes, teaching staff, and AI Study Assistant grants for that Course across its Cohorts. A user can be an Instructor for one Course and a Learner in another Course in the same Study Server.
_Avoid_: Teacher, professor, course owner

**TA**:
A Cohort-scoped support role. A TA helps learners in one Cohort, including Office Hours and questions routed from the AI Study Assistant. A user can be a TA in one Cohort and a Learner in another Cohort or Course in the same Study Server.
_Avoid_: Moderator, helper, assistant

**Learner**:
A person participating in a Cohort through Enrollment. Learner is not a global identity—a user becomes a Learner only where they are enrolled.
_Avoid_: Student, member, user

**Study Server Channel**:
A channel in a Study Server that is not tied to a specific Course or Cohort. Used for announcements, community discussion, and other server-wide spaces. Access is controlled by Study Server membership and governance roles.
_Avoid_: General channel, public channel

**Course Channel**:
A channel tied to a Course. Learners, TAs, and Instructors access it through Cohort Enrollment and their roles—not through blanket Study Server access. A Cohort usually uses the Course's channels unless a Cohort-specific channel is added.
_Avoid_: Module channel, classroom channel

**Cohort Channel**:
A channel tied to one Cohort. Created only when the Instructor opts in—for example when a Cohort needs a private discussion space separate from other Cohorts taking the same Course. Not auto-created for every Cohort.
_Avoid_: Section channel, group chat

**Voice Channel**:
A Study Server channel where members can join voice conversation in real time. Available in the education MVP as part of the familiar Discord-like community experience.
_Avoid_: Voice room, call, meeting

**TA Queue**:
An async support queue for a Cohort. Learner questions—including those routed when the AI Study Assistant is not confident—can wait here for a TA to pick up. Works alongside Office Hours: the queue handles help anytime; Office Hours is the scheduled live window for the same Cohort.
_Avoid_: Ticket, help desk, inbox

**Support Question**:
A learner question tracked through the learning-support workflow. It can be AI-answered, human-answered, unanswered, marked duplicate, or promoted to an FAQ candidate.
_Avoid_: Ticket, help request, thread

**Alumni**:
A former Learner whose Cohort or Course has ended. Alumni get limited read access only where the Instructor allows—for example past resources or announcements—not automatic access to active Cohort spaces.
_Avoid_: Graduate, ex-student, former member

**Guest**:
A temporary participant with invite-only access to specific Study Server, Course, or Cohort spaces. Guests do not get blanket access to the whole Study Server.
_Avoid_: Visitor, anonymous user, trial user

**Study Server Member**:
Someone who belongs to a Study Server through an invite or through Enrollment in at least one Cohort. Study Server Channels are visible to members—not to users who have not joined.
_Avoid_: Server member, subscriber, follower

**Approved FAQ**:
A trusted answer an Instructor approves from a repeated or resolved Support Question. The AI Study Assistant may cite Approved FAQs alongside Course Resources when answering in granted channels.
_Avoid_: Pin, wiki page, knowledge base article

**Channel Summary**:
A generated recap of important discussion in a Course Channel over a time period. Helps Instructors and Learners review what happened without reading every message.
_Avoid_: Digest, recap, transcript

**Direct Message**:
A private text conversation between two users on the platform. Users can message friends after a friend request is accepted, similar to Discord. Education-specific policy controls for cross-role messaging may be added later. Live delivery and the conversation UI are implemented in the Friends Hub slice (#31); voice calls between friends are a separate slice (#32).
_Avoid_: DM thread, private chat

**Friend Request**:
A request from one user to another to become friends on the platform. The recipient must accept before Direct Messages are available. For education deployments, friend requests should be limited to users who share at least one Study Server membership (co-membership); see `docs/product-design/visibility-and-social-model.md`.
_Avoid_: Connection, follow

**Friends Hub**:
The platform-wide social sidebar where a user sees accepted friends, their online/offline presence, and opens a Direct Message conversation. Analogous to Discord's friends list and DM panel—not a Study Server or Course Channel. Course channels and cohort rosters remain enrollment-scoped; the Friends list is global.
_Avoid_: Contacts, buddy list, social feed

**Friend Presence**:
Platform-wide online state for friends (for example online, idle, offline), distinct from Voice Channel presence in a Study Server. Delivered through the Realtime Service over WebSocket subscriptions.
_Avoid_: Activity status, last seen (unless explicitly productized later)

**DM Voice Call**:
A private 1:1 voice conversation between two friends, initiated from the Friends Hub or DM header. Uses WebRTC/LiveKit for audio and the Realtime Service for call signaling; friendship and block checks mirror Direct Message rules.
_Avoid_: Phone call, meeting, Voice Channel

**Instructor Dashboard**:
An Instructor-facing view of actionable learning operations for a Study Server or Course: unanswered Support Questions, repeated questions, Approved FAQs, Office Hours and TA Queue load, engagement signals, and AI Study Assistant usage.
_Avoid_: Analytics page, admin panel, reports

**SaaS Plan**:
The subscription tier attached to a Study Server. The Study Server Owner pays for the plan, which controls limits such as AI usage, resource capacity, and advanced dashboard access.
_Avoid_: Subscription, billing plan, package

**Verified Educator**:
A post-MVP profile badge showing an educator has been verified. It is a trust signal only and does not grant permissions inside a Study Server.
_Avoid_: Certified teacher, blue check

**Organization**:
An optional container above Study Servers for a school, bootcamp, or tutoring business. Not required for MVP. Added later for centralized billing, policy, roster import, and SSO when customers need it.
_Avoid_: Workspace, tenant, account

## Related documentation

| Doc | Role |
|---|---|
| [`docs/product-design/README.md`](docs/product-design/README.md) | Product showcase index — mockups, vision, journeys |
| [`docs/product-design/visibility-and-social-model.md`](docs/product-design/visibility-and-social-model.md) | Global friends vs enrollment-scoped course sidebar |
| [`docs/product/education-mvp-prd.md`](docs/product/education-mvp-prd.md) | Requirements and user stories |
| [`plan.md`](../plan.md) | Implementation milestones and frontend direction |
| [`System Design.md`](../System Design.md) | Backend services and engineering diagrams |
| [`docs/operations/agent-workflow.md`](docs/operations/agent-workflow.md) | **Mandatory agent workflow** — issue order, loop, owner-only merge |
| [`HANDOFF.md`](../HANDOFF.md) | Agent session startup and current slice |
