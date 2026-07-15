export type CommunityAnnouncementStatus = 'PUBLISHED' | 'ARCHIVED'

export type CommunityAnnouncement = {
  id: string
  studyServerId: string
  authorUserId: string
  authorDisplayName: string
  title: string
  body: string
  status: CommunityAnnouncementStatus
  createdAt: string
  updatedAt: string
  likeCount: number
  viewerLiked: boolean
  canEdit: boolean
}

export type CommunityAnnouncementListResponse = {
  announcements: CommunityAnnouncement[]
}

export type CreateCommunityAnnouncementInput = {
  title: string
  body: string
}
