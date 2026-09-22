import { apiFetch } from '../../lib/api-client'
import { getApiBase } from '../../lib/api-base'

export type ExportPart = { source: string; state: string; errorCode: string | null }
export type ExportJob = {
  schemaVersion: 1
  id: string
  accountId: string
  requestedAt: string
  expiresAt: string
  state: 'BUILDING' | 'READY' | 'CANCELLED' | 'EXPIRED'
  cleanupPending: boolean
  parts: ExportPart[]
}
const base = '/api/v1/auth/account/exports'
function jobPath(id: string) {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) throw new Error('Invalid export request')
  return `${base}/${id}`
}
export function listExports(signal?: AbortSignal) { return apiFetch<ExportJob[]>(base, { signal }) }
export function createExport(requestId: string, signal?: AbortSignal) {
  return apiFetch<ExportJob>(base, { method: 'POST', body: JSON.stringify({ requestId }), signal })
}
export function cancelExport(id: string, signal?: AbortSignal) { return apiFetch<ExportJob>(jobPath(id), { method: 'DELETE', signal }) }
export async function authorizeExportDownload(id: string, signal?: AbortSignal) {
  const url = new URL(`${getApiBase()}${jobPath(id)}/download`, window.location.origin)
  if (url.origin !== window.location.origin) throw new Error('Browser downloads require the deployment’s same-origin API')
  await apiFetch<void>(`${jobPath(id)}/download-authorization`, { method: 'POST', signal })
  return url.href
}
