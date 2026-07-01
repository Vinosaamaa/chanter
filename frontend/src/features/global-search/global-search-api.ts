import { apiFetch } from '../../lib/api-client'

import type { GlobalSearchResponse, ReindexResponse } from './global-search-types'

export async function searchStudyServer(
  studyServerId: string,
  query: string,
): Promise<GlobalSearchResponse> {
  const params = new URLSearchParams({ q: query })
  return apiFetch<GlobalSearchResponse>(
    `/api/v1/study-servers/${studyServerId}/search?${params.toString()}`,
  )
}

export async function reindexStudyServer(studyServerId: string): Promise<ReindexResponse> {
  return apiFetch<ReindexResponse>(`/api/v1/study-servers/${studyServerId}/search/reindex`, {
    method: 'POST',
  })
}
