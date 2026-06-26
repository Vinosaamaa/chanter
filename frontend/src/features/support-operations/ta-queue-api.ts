import { apiFetch } from '../../lib/api-client'
import type { TaQueueItem } from '../questions/support-question-types'

import type { TaQueueListResponse } from './support-operations-types'

export async function listTaQueueItems(
  cohortId: string,
  viewerUserId: string,
): Promise<TaQueueListResponse> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<TaQueueListResponse>(
    `/api/v1/cohorts/${cohortId}/ta-queue?${params.toString()}`,
  )
}

export async function pickupTaQueueItem(
  cohortId: string,
  itemId: string,
  actorUserId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue/${itemId}/pickup`, {
    method: 'PATCH',
    body: JSON.stringify({ actorUserId }),
  })
}

export async function resolveTaQueueItem(
  cohortId: string,
  itemId: string,
  actorUserId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue/${itemId}/resolve`, {
    method: 'PATCH',
    body: JSON.stringify({ actorUserId }),
  })
}
