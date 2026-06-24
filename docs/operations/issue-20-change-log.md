# Issue 20 Change Log: Promote Repeated Support Question To Approved FAQ

Date: 2026-06-23  
Branch: `feature/20-promote-support-question-to-faq`  
Issue: `#20 Slice: Promote Repeated Support Question To Approved FAQ`

## Acceptance Criteria Covered

- Similar Support Questions are grouped by token Jaccard similarity (>= 0.5, min 3-char tokens, stop words).
- Instructor can approve/edit Approved FAQ from FAQ candidates (`POST /api/v1/courses/{courseId}/approved-faqs`).
- Approved FAQ is searchable (`GET .../approved-faqs/search?query=`) and assistant-accessible via agent-service grounding.
- Tests cover permissions and retrieval (`ApprovedFaqSmokeTest`).

## 1. Message Service — Approved FAQ Storage And APIs

- Flyway `V4__create_approved_faq_tables.sql` for `approved_faqs` and `approved_faq_source_questions`.
- `FaqCandidateGrouper` clusters Support Questions in a Course Channel by token similarity.
- `ApprovedFaqRepository` JDBC, `ApprovedFaqService`, and `ApprovedFaqController`:
  - `GET /api/v1/course-channels/{channelId}/faq-candidates` (Instructor via `canViewUnansweredSupportQuestions`)
  - `POST /api/v1/courses/{courseId}/approved-faqs` (create/update with source Support Question ids)
  - `GET /api/v1/courses/{courseId}/approved-faqs` (enrolled learner or Instructor)
  - `GET /api/v1/courses/{courseId}/approved-faqs/search?query=` (enrolled learner or Instructor)
- Extended `SupportQuestionRepository` with `findByChannelId`.
- Course enrollment checks reuse community `resource-access` via `CourseResourceAccessClient`.

## 2. Agent Service — FAQ Grounding

- `ApprovedFaqClient` HTTP client to message-service.
- `GroundedSupportQuestionService` adds Approved FAQs as `GroundingSource` entries alongside AI-approved Course Resources.

## 3. Gateway And Frontend Demo

- Gateway route `message-service-approved-faqs` for approved FAQ and FAQ candidate paths.
- Frontend demo section **Approved FAQs (#20)**: list candidates, approve form, list/search FAQs.

## Verification

- `mvn -pl message-service,agent-service verify`

## Deferred

- Real caller identity remains `TODO(#auth)` / issue #30.
- FAQ edit UI beyond create/update POST deferred; API supports update when `id` is supplied.
