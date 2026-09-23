import { ApiError, apiFetch } from '../../lib/api-client'
import { getApiBase } from '../../lib/api-base'

export type DeletionJob = {
  id: string
  state: 'PREPARING' | 'BLOCKED_OWNERSHIP' | 'PREPARED' | 'PREPARATION_EXPIRED' | 'CANCELLING' | 'CANCELLED' | 'ERASING' | 'WAITING_FOR_REPLICA' | 'COMPLETE'
  createdAt: string
  preparationExpiresAt: string
  replicationPending: boolean
  preparationError: string | null
  parts: Array<{ source: string; state: 'PENDING' | 'COMPLETE' | 'PRESERVED'; errorCode: string | null }>
}
const base = '/api/v1/auth/account/deletions'
export function validDeletionId(id: string) { return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id) }
function jobPath(id: string) {
  if (!validDeletionId(id)) throw new Error('Invalid deletion request')
  return `${base}/${id}`
}
export function prepareDeletion(id: string, signal?: AbortSignal) {
  jobPath(id)
  return apiFetch<DeletionJob>(base, { method: 'POST', body: JSON.stringify({ requestId: id }), signal })
}
export function getDeletion(id: string, signal?: AbortSignal) { return apiFetch<DeletionJob>(jobPath(id), { signal }) }
export function cancelDeletion(id: string, signal?: AbortSignal) { return apiFetch<DeletionJob>(jobPath(id), { method: 'DELETE', signal }) }
export function confirmDeletion(id: string, signal?: AbortSignal) {
  // Confirmation can revoke this session. Inspect its cookie-bound receipt on 401
  // before a failed refresh can erase the current job's navigation context.
  return apiFetch<DeletionJob>(`${jobPath(id)}/confirm`, { method: 'POST', body: JSON.stringify({ confirmation: 'DELETE MY ACCOUNT' }), signal, refreshOnUnauthorized: false })
}
export async function getDeletionReceipt(id: string, signal?: AbortSignal): Promise<DeletionJob> {
  const url = new URL(`${getApiBase()}${jobPath(id)}/receipt`, window.location.origin)
  if (url.origin !== window.location.origin) throw new Error('Account receipts require the same-origin API')
  // Receipt authority is exclusively its HttpOnly cookie. Never refresh a revoked session.
  const response = await fetch(url.href, { method: 'GET', credentials: 'include', cache: 'no-store', signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new ApiError('Receipt unavailable', response.status)
  return response.json() as Promise<DeletionJob>
}
