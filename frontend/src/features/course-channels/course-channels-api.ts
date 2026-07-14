import { apiFetch } from '../../lib/api-client'

export type CourseChannelKind = 'TEXT' | 'VOICE'

export type CourseChannel = {
  id: string
  cohortId: string
  name: string
  kind: CourseChannelKind
}

export async function createCourseChannel(
  cohortId: string,
  input: { name: string; kind: CourseChannelKind },
): Promise<CourseChannel> {
  return apiFetch<CourseChannel>(`/api/v1/cohorts/${cohortId}/channels`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function renameCourseChannel(
  channelId: string,
  name: string,
): Promise<CourseChannel> {
  return apiFetch<CourseChannel>(`/api/v1/course-channels/${channelId}`, {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  })
}

export async function archiveCourseChannel(channelId: string): Promise<void> {
  await apiFetch<void>(`/api/v1/course-channels/${channelId}`, {
    method: 'DELETE',
  })
}
