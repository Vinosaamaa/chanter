import { apiFetch } from '../../lib/api-client'
import type { HomeSummaryResponse } from './home-summary-types'

export function homeSummaryQueryKey(userId: string | undefined) {
  return ['home-summary', userId] as const
}

export function fetchHomeSummary(): Promise<HomeSummaryResponse> {
  return apiFetch<HomeSummaryResponse>('/api/v1/me/home-summary')
}
