import { apiFetch } from '../../lib/api-client'

import type { CourseLifecycle, CourseLifecycleCohort, StudyServerInvitation } from './course-lifecycle-types'

export async function fetchCourseLifecycle(courseId: string): Promise<CourseLifecycle> {
  return apiFetch<CourseLifecycle>(`/api/v1/courses/${encodeURIComponent(courseId)}`)
}

export async function updateCourseMetadata(
  courseId: string,
  input: { title: string; description?: string },
): Promise<CourseLifecycle> {
  return apiFetch<CourseLifecycle>(`/api/v1/courses/${encodeURIComponent(courseId)}`, {
    method: 'PATCH',
    body: JSON.stringify({
      title: input.title,
      description: input.description ?? '',
    }),
  })
}

export async function addCourseCohort(courseId: string, name: string): Promise<CourseLifecycleCohort> {
  return apiFetch<CourseLifecycleCohort>(`/api/v1/courses/${encodeURIComponent(courseId)}/cohorts`, {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export async function assignCourseInstructor(
  courseId: string,
  input: { instructorUserId?: string; instructorEmail?: string },
): Promise<void> {
  await apiFetch<void>(`/api/v1/courses/${encodeURIComponent(courseId)}/instructor`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export async function publishCourse(courseId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/courses/${encodeURIComponent(courseId)}/publish`, {
    method: 'POST',
  })
}

export async function unpublishCourse(courseId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/courses/${encodeURIComponent(courseId)}/unpublish`, {
    method: 'POST',
  })
}

export async function archiveCourse(courseId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/courses/${encodeURIComponent(courseId)}/archive`, {
    method: 'POST',
  })
}

export async function fetchPendingStudyServerInvitations(): Promise<StudyServerInvitation[]> {
  return apiFetch<StudyServerInvitation[]>('/api/v1/study-server-invitations')
}

export async function acceptStudyServerInvitation(
  studyServerId: string,
  invitationId: string,
): Promise<void> {
  await apiFetch<void>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/invitations/${encodeURIComponent(invitationId)}/accept`,
    { method: 'POST' },
  )
}
