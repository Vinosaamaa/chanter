import { apiFetch } from '../../lib/api-client'
import type { StudyServerNavigation, StudyServerSummary } from './types'
import type { SourceDeletionAccepted } from '../account-data/source-deletion-api'

export function fetchAccessibleStudyServers(): Promise<StudyServerSummary[]> {
  return apiFetch<StudyServerSummary[]>('/api/v1/study-servers')
}

export function fetchStudyServerNavigation(studyServerId: string): Promise<StudyServerNavigation> {
  return apiFetch<StudyServerNavigation>(`/api/v1/study-servers/${studyServerId}/navigation`)
}

export function deleteStudyServer(studyServerId: string, signal?: AbortSignal): Promise<SourceDeletionAccepted> {
  return apiFetch<SourceDeletionAccepted>(`/api/v1/study-servers/${studyServerId}`, {
    method: 'DELETE',
    signal,
  })
}
