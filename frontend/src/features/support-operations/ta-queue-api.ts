import { apiFetch } from '../../lib/api-client'
import type { TaQueueItem } from '../questions/support-question-types'

import type { TaQueueListResponse } from './support-operations-types'

export async function listTaQueueItems(
  cohortId: string,
): Promise<TaQueueListResponse> {
  return apiFetch<TaQueueListResponse>(
    `/api/v1/cohorts/${cohortId}/ta-queue`,
  )
}

export async function pickupTaQueueItem(
  cohortId: string,
  itemId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue/${itemId}/pickup`, {
    method: 'PATCH',
  })
}

export async function resolveTaQueueItem(
  cohortId: string,
  itemId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue/${itemId}/resolve`, {
    method: 'PATCH',
  })
}

export async function cancelTaQueueItem(
  cohortId: string,
  itemId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue/${itemId}/cancel`, {
    method: 'PATCH',
  })
}
