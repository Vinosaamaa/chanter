import { apiFetch } from '../../lib/api-client'
import type {
  CreatedStudyServerInvitation,
  StudyServerMemberFilter,
  StudyServerMemberListResponse,
  StudyServerMemberSummary,
} from './community-member-types'

export function communityMembersQueryKey(
  studyServerId: string,
  search: string,
  filter: StudyServerMemberFilter,
) {
  return ['community-members', studyServerId, search, filter] as const
}

export function communityMemberSummaryQueryKey(studyServerId: string) {
  return ['community-member-summary', studyServerId] as const
}

export function fetchStudyServerMembers(
  studyServerId: string,
  options: { search?: string; filter?: StudyServerMemberFilter; limit?: number; offset?: number } = {},
): Promise<StudyServerMemberListResponse> {
  const params = new URLSearchParams()
  if (options.search) params.set('search', options.search)
  params.set('filter', options.filter ?? 'ALL')
  if (options.limit != null) params.set('limit', String(options.limit))
  if (options.offset != null) params.set('offset', String(options.offset))
  return apiFetch<StudyServerMemberListResponse>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/members?${params}`,
  )
}

export function fetchStudyServerMemberSummary(
  studyServerId: string,
): Promise<StudyServerMemberSummary> {
  return apiFetch<StudyServerMemberSummary>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/member-summary`,
  )
}

export function createStudyServerInvitations(
  studyServerId: string,
  inviteEmails: string[],
): Promise<CreatedStudyServerInvitation[]> {
  return apiFetch<CreatedStudyServerInvitation[]>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/invitations`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ inviteEmails }),
    },
  )
}
