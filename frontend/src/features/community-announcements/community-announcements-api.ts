import { apiFetch } from '../../lib/api-client'
import type {
  CommunityAnnouncement,
  CommunityAnnouncementListResponse,
  CommunityAnnouncementStatus,
  CreateCommunityAnnouncementInput,
} from './community-announcement-types'

export function communityAnnouncementsQueryKey(
  studyServerId: string,
  status: CommunityAnnouncementStatus = 'PUBLISHED',
) {
  return ['community-announcements', studyServerId, status] as const
}

export function fetchCommunityAnnouncements(
  studyServerId: string,
  status: CommunityAnnouncementStatus = 'PUBLISHED',
): Promise<CommunityAnnouncementListResponse> {
  const params = new URLSearchParams({ status })
  return apiFetch<CommunityAnnouncementListResponse>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/announcements?${params}`,
  )
}

export function createCommunityAnnouncement(
  studyServerId: string,
  input: CreateCommunityAnnouncementInput,
): Promise<CommunityAnnouncement> {
  return apiFetch<CommunityAnnouncement>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/announcements`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export function updateCommunityAnnouncement(
  studyServerId: string,
  announcementId: string,
  input: CreateCommunityAnnouncementInput,
): Promise<CommunityAnnouncement> {
  return apiFetch<CommunityAnnouncement>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/announcements/${encodeURIComponent(announcementId)}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  )
}

export function archiveCommunityAnnouncement(
  studyServerId: string,
  announcementId: string,
): Promise<CommunityAnnouncement> {
  return apiFetch<CommunityAnnouncement>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/announcements/${encodeURIComponent(announcementId)}/archive`,
    { method: 'POST' },
  )
}

export function upsertCommunityAnnouncementLike(
  studyServerId: string,
  announcementId: string,
  liked: boolean,
): Promise<CommunityAnnouncement> {
  return apiFetch<CommunityAnnouncement>(
    `/api/v1/study-servers/${encodeURIComponent(studyServerId)}/announcements/${encodeURIComponent(announcementId)}/reactions`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ liked }),
    },
  )
}
