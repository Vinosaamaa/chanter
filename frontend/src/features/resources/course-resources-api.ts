import { ApiError, apiFetch, apiFetchBlob } from '../../lib/api-client'

import type {
  CourseResource,
  CourseResourceAccess,
  CourseResourceListResponse,
} from './course-resource-types'

export async function fetchCourseResourceAccess(courseId: string): Promise<CourseResourceAccess> {
  return apiFetch<CourseResourceAccess>(`/api/v1/courses/${courseId}/resource-access`)
}

export async function listCourseResources(courseId: string): Promise<CourseResourceListResponse> {
  return apiFetch<CourseResourceListResponse>(`/api/v1/courses/${courseId}/course-resources`)
}

export async function uploadCourseResource(
  courseId: string,
  file: File,
  options: { title?: string; aiApproved: boolean },
): Promise<CourseResource> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('title', options.title?.trim() || file.name)
  formData.append('aiApproved', String(options.aiApproved))

  return apiFetch<CourseResource>(`/api/v1/courses/${courseId}/course-resources`, {
    method: 'POST',
    body: formData,
  })
}

export async function downloadCourseResourceContent(resourceId: string): Promise<Blob> {
  return apiFetchBlob(`/api/v1/course-resources/${resourceId}/content`)
}

export function resourceAccessDeniedMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return 'Course resources are only available to enrolled learners and course instructors.'
  }
  if (error instanceof ApiError && error.status === 404) {
    return 'This course or resource could not be found.'
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Unable to load course resources.'
}
