import { apiFetch } from '../../lib/api-client'

import type { GlobalSearchHit, GlobalSearchResponse, ReindexResponse } from './global-search-types'

export async function searchStudyServer(
  studyServerId: string,
  query: string,
  filters: { documentType?: GlobalSearchHit['documentType']; courseId?: string } = {},
): Promise<GlobalSearchResponse> {
  const params = new URLSearchParams({ q: query })
  if (filters.documentType) params.set('type', filters.documentType)
  if (filters.courseId) params.set('courseId', filters.courseId)
  return apiFetch<GlobalSearchResponse>(
    `/api/v1/study-servers/${studyServerId}/search?${params.toString()}`,
  )
}

export async function reindexStudyServer(studyServerId: string): Promise<ReindexResponse> {
  return apiFetch<ReindexResponse>(`/api/v1/study-servers/${studyServerId}/search/reindex`, {
    method: 'POST',
  })
}
