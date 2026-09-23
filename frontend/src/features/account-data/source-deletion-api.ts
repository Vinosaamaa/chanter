import { apiFetch } from '../../lib/api-client'

export type SourceDeletionAccepted = { jobId: string; targetId: string; state: 'PENDING' }
export type SourceDeletionJob = {
  jobId: string
  targetKind: 'STUDY_SERVER' | 'RESOURCE'
  targetId: string
  state: 'ERASING' | 'WAITING_FOR_REPLICA' | 'COMPLETE'
  replicationPending: boolean
  parts: Array<{ source: string; state: 'PENDING' | 'COMPLETE' | 'PRESERVED'; errorCode: string | null }>
}
export function getSourceDeletion(id: string, signal?: AbortSignal): Promise<SourceDeletionJob> {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(id)) throw new Error('Invalid deletion request')
  return apiFetch<SourceDeletionJob>(`/api/v1/auth/account/source-deletions/${id}`, { signal })
}
