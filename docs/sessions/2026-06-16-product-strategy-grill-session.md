# Product Strategy And Grill Session

Date: 2026-06-16

## Purpose

This note captures the product strategy discussion and the beginning of the `/grill-with-docs` session for Chanter. It is meant as a readable recap so future sessions can recover the reasoning behind the current MVP direction.

## Current Product Direction

Chanter is no longer framed as a generic Discord clone. The selected first wedge is:

> Discord for learning communities, with AI teaching assistants and instructor operations built in.

The initial product is an education-focused SaaS platform for Study Servers. Target buyers are educators, bootcamps, tutoring businesses, cohort-based course creators, and learning communities.

## Why Not Just Discord

The concern was raised that Discord is already free, familiar, and popular. The conclusion was that Chanter probably cannot win as "Discord but slightly different."

Chanter has a stronger business case only if it sells to buyer pain:

- Repeated student questions.
- Buried course knowledge.
- Manual office-hour logistics.
- Weak instructor analytics.
- Fragmented Discord plus LMS plus bots workflows.
- Unsafe or uncontrolled AI bots.

The product should sell education operations outcomes, not chat novelty:

- Lower instructor/TA workload.
- Faster learner support.
- Reusable course knowledge.
- Safer AI assistance.
- Better instructor visibility.

If a community only needs casual chat, Discord is probably the better choice.

## MVP Scope

The MVP is a Study Server product:

- Study Servers for courses, cohorts, tutoring groups, bootcamps, and learning communities.
- Course/module channels.
- Instructor, TA, learner, alumni, guest, and owner roles.
- Course resources.
- AI Study Assistant.
- Question workflow.
- FAQ candidate generation.
- Office-hours queue.
- Instructor analytics.
- SaaS plans and AI usage quotas later in the MVP path.

The first demo path should prove an education workflow:

1. An educator creates a Study Server.
2. Learners ask course questions.
3. The AI Study Assistant answers from approved resources.
4. Low-confidence questions route to human help.
5. Instructors see repeated confusion and support load in a dashboard.
6. A useful answer can become an approved FAQ or study note.

## Identity And Social Model

The selected identity direction is:

- Users have global accounts.
- Roles are scoped per Study Server.
- A user can be an instructor in one Study Server and a learner in another.
- Teacher/instructor powers are assigned by Study Server admins or organization policy, not self-declared globally.
- Organizations/workspaces are optional at first and become important later for schools, bootcamps, SSO, domain verification, roster import, centralized billing, and policy enforcement.
- Friend requests and DMs may exist later, but education deployments need consent, block/report controls, and policy settings for student-teacher messaging.

Recommended hierarchy:

```text
Global User Account
  -> optional Organization or Workspace
    -> Study Server
      -> Channels
      -> Members
      -> Roles
      -> Permissions
```

## Later Monetization: Creator Course Commerce

A later business direction was added:

Instructors can sell courses inside a Study Server. Learners can buy/enroll, and the purchase unlocks the right channels, resources, live classes, AI Study Assistant access, and office-hours policies.

This moves Chanter toward a community-native course platform:

- Discord-like community.
- AI-assisted learning operations.
- Live classes.
- Udemy/Circle/Skool-style course commerce.

Potential later features:

- Instructor course storefront.
- Paid course listings.
- Course purchase and enrollment.
- Channel/resource/live-class access grants.
- Live class rooms.
- Recordings, transcripts, summaries, and action items.
- Instructor revenue dashboard.
- Platform take-rate, subscriptions, or hybrid pricing.

This is intentionally later, not MVP, because it adds payments, refunds, taxes, creator trust, fraud prevention, content moderation, and live-video reliability.

## GitHub And Skills State

The GitHub repository exists:

- `https://github.com/Vinosaamaa/chanter`
- Local `main` tracks `origin/main`.

Installed external skills include:

- `grill-with-docs`
- `to-prd`
- `to-issues`
- `tdd`
- `improve-codebase-architecture`
- `drawio-skill`
- `greploop`

`greploop` was installed from `https://github.com/greptileai/skills`, but it is only useful after there is an open PR and Greptile is enabled on the GitHub repository.

## Docs Updated Before This Note

Important docs now include:

- `README.md`
- `HANDOFF.md`
- `plan.md`
- `System Design.md`
- `docs/product/education-mvp-prd.md`
- `docs/product-design/` — product showcase (mockups, vision, user journey); added 2026-06-17
- `docs/issues/education-mvp-issue-breakdown.md`
- `docs/operations/project-operations-bootstrap.md`
- `docs/diagrams/*.drawio`
- `docs/diagrams/*.drawio.png`

## `/grill-with-docs` State

The grill started by checking for existing glossary/ADR files:

- No `CONTEXT.md` exists yet.
- No `docs/adr/` exists yet.

The first unresolved design question is the relationship between Study Server and Course.

Recommended answer:

> A Study Server is the community container; Courses are sellable/enrollable learning products inside it.

Reasoning:

- A single instructor or school may want one community with multiple courses.
- Course commerce later needs Course as a purchasable product.
- Study Server should contain channels, members, roles, permissions, support workflows, and community context.
- Course should contain curriculum, pricing/enrollment later, resources, live class schedule, and access rules.
- This avoids forcing one server per course while still allowing one-server-one-course setups as a simple case.

Pending question to resume:

Should the canonical relationship be:

1. Study Server is the community container; Courses are sellable/enrollable learning products inside it.
2. One Study Server equals one Course.
3. Organization owns Courses; each Course creates one or more Study Servers/cohorts.
4. Course is the main object; Study Server is just the chat/community attached to it.

## Grill Decisions After Resume

### Study Server vs Course

Decision:

- A Study Server is the community container.
- Courses are sellable/enrollable learning products inside a Study Server.

Captured in `CONTEXT.md`:

- `Study Server`
- `Course`

### Course vs Cohort

Decision:

- A Course is reusable curriculum/product.
- A Cohort is one scheduled group of learners taking a Course together.
- A Course can have many Cohorts.

Captured in `CONTEXT.md`:

- `Cohort`

### Course Purchase And Access

Decision:

- Enrollment grants access only to the Course/Cohort spaces inside a Study Server.
- Enrollment should not grant blanket access to the whole Study Server.

Captured in `CONTEXT.md`:

- `Enrollment`

### Live Classes

Decision:

- A Live Class belongs to a Cohort.
- Live Class access should follow Cohort enrollment.

Captured in `CONTEXT.md`:

- `Live Class`

### Course Resources

Decision:

- Course Resources belong to a Course by default.
- A Cohort can have Cohort-specific resources for schedules, recordings, transcripts, and temporary materials.

Captured in `CONTEXT.md`:

- `Course Resource`

### AI Study Assistant Scope

Question:

- Should the AI Study Assistant be one per Study Server, one per Course/Cohort, or global across Study Servers?

Decision:

- Install one AI Study Assistant per Study Server.
- Grant it explicit access per channel, Course, Cohort, and Course Resource.
- It is one installation, but in each channel it can feel like a different helper because its allowed context and grounding change with those grants.

Captured in `CONTEXT.md`:

- `AI Study Assistant`

### Low-Confidence Handling

Question:

- When the AI Study Assistant is not confident in an answer, what should happen?

Decision:

- It should say it is unsure, cite what it did find if anything, and route the question to a human support path such as a TA queue or office hours.
- It should not guess or block the learner's message flow.

Captured in `CONTEXT.md`:

- Updated `AI Study Assistant`

### Office Hours

Question:

- What should "Office Hours" mean in Chanter?

Decision:

- Office Hours is a scheduled live support window for a Cohort.
- Enrolled learners can join during the window.
- Low-confidence AI Study Assistant questions can route into the same support flow.

Captured in `CONTEXT.md`:

- `Office Hours`

### Study Server Roles

Question:

- Should Instructor, TA, and Learner be Study Server-scoped roles, or can they differ by Course and Cohort within the same Study Server?

Decision:

- Users have global accounts.
- Governance is Study Server-scoped: Study Server Owner governs the community and assigns Instructors to Courses.
- Teaching and learning roles are scoped below the Study Server:
  - Instructor is Course-scoped.
  - TA is Cohort-scoped.
  - Learner comes from Enrollment in a Course or Cohort.
- The same user can be an Instructor for one Course, a TA for one Cohort, and a Learner in another Course or Cohort within the same Study Server.

Captured in `CONTEXT.md`:

- `Study Server Owner`
- `Instructor`
- `TA`
- `Learner`

### Channel Structure

Question:

- How should channels be organized inside a Study Server?

Decision:

- Use Study Server-wide channels for announcements and community spaces.
- Use Course channels tied to a Course for learning discussion.
- A Cohort usually uses the Course's channels unless a Cohort-specific channel is added.
- Access follows Enrollment and role, not blanket Study Server membership.

Captured in `CONTEXT.md`:

- `Study Server Channel`
- `Course Channel`

### Cohort Channels

Question:

- When should a Cohort get its own channel(s), separate from Course channels?

Decision:

- Cohort Channels are opt-in only.
- The Instructor creates a Cohort Channel when that group needs a private space.
- New Cohorts do not automatically get their own channel set.

Captured in `CONTEXT.md`:

- `Cohort Channel`

### Human Support Outside Office Hours

Question:

- When the AI Study Assistant routes a low-confidence question to humans, what happens outside scheduled Office Hours?

Decision:

- Use both async TA Queue and scheduled Office Hours.
- The TA Queue handles questions anytime.
- Office Hours is the live support window for the same Cohort.

Captured in `CONTEXT.md`:

- `TA Queue`

### Alumni And Guest

Question:

- What happens when a Cohort ends, and what is a Guest?

Decision:

- Alumni are former Learners with limited read access only where the Instructor allows—not automatic access to active Cohort spaces.
- Guests are temporary, invite-only participants with access only to specific spaces.

Captured in `CONTEXT.md`:

- `Alumni`
- `Guest`

### Study Server Membership

Question:

- Who can see Study Server-wide channels? Must you be enrolled in a Course, or can you join the server without taking a class?

Decision:

- Study Server membership requires an invite or Enrollment in at least one Course or Cohort.
- Study Server Channels are visible only to members.

Captured in `CONTEXT.md`:

- `Study Server Member`

### Approved FAQ

Question:

- When the same question keeps coming up, how does trusted knowledge get saved for the AI Study Assistant?

Decision:

- Instructors approve FAQ entries from repeated or resolved questions.
- The AI Study Assistant may cite Approved FAQs alongside Course Resources.

Captured in `CONTEXT.md`:

- `Approved FAQ`

### Organization

Question:

- Is an Organization a first-class concept in the MVP?

Decision:

- Organization is optional and post-MVP.
- Study Server Owner is enough to start.
- Add Organizations later for schools/bootcamps that need centralized billing, policy, roster import, and SSO.

Captured in `CONTEXT.md`:

- `Organization`

### Enrollment Scope

Question:

- When a learner joins a class, what do they enroll in?

Decision:

- Enrollment is primarily to a Cohort.
- Course access follows from Cohort membership.

Captured in `CONTEXT.md`:

- Updated `Enrollment`

### Live Class In MVP

Question:

- Is built-in Live Class video in the education MVP?

Decision:

- Built-in Live Class video is out of MVP.
- Live Class remains a domain term for the later live-teaching phase.

Captured in `CONTEXT.md`:

- Updated `Live Class`

### Direct Messages

Question:

- Are direct messages in the education MVP?

Decision:

- Yes — full Discord-style friend DMs in MVP.
- Users send friend requests; recipients accept; then Direct Messages are available platform-wide.
- Channels, TA Queue, and Office Hours remain the primary learning-support workflows.

Captured in `CONTEXT.md`:

- `Direct Message`
- `Friend Request`

### Voice In MVP

Question:

- How far should voice go in MVP?

Decision:

- Study Server Voice Channels are in MVP — Discord-style voice rooms members can join.
- Built-in Live Class video remains post-MVP.

Captured in `CONTEXT.md`:

- `Voice Channel`

### Verified Educator

Question:

- Verified educator badge in MVP or later?

Decision:

- Post-MVP profile badge only.
- Does not grant permissions.

Captured in `CONTEXT.md`:

- `Verified Educator`

### Support Question

Question:

- What do we call a learner question in the AI/human/FAQ workflow?

Decision:

- Use Support Question.

Captured in `CONTEXT.md`:

- `Support Question`

### SaaS Payer

Question:

- Who pays for Chanter in the education MVP?

Decision:

- The Study Server Owner pays the SaaS Plan for their Study Server.

Captured in `CONTEXT.md`:

- `SaaS Plan`

### Grill Session Status

The education MVP glossary grill is complete. Next step: align PRD and issue breakdown with `CONTEXT.md`, then GitHub Projects bootstrap.


