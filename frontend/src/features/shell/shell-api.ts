import { apiFetch } from '../../lib/api-client'
import type { StudyServerNavigation, StudyServerSummary } from './types'

export function fetchAccessibleStudyServers(): Promise<StudyServerSummary[]> {
  return apiFetch<StudyServerSummary[]>('/api/v1/study-servers')
}

export function fetchStudyServerNavigation(studyServerId: string): Promise<StudyServerNavigation> {
  return apiFetch<StudyServerNavigation>(`/api/v1/study-servers/${studyServerId}/navigation`)
}

export function deleteStudyServer(studyServerId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/study-servers/${studyServerId}`, {
    method: 'DELETE',
  })
}
