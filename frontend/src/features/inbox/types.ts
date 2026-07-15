export type NotificationFilter = 'ALL' | 'MENTIONS' | 'ANNOUNCEMENTS'
export type NotificationStatus = 'OPEN' | 'DONE' | 'ALL'

export type NotificationKind =
  | 'SUPPORT_QUESTION_ANSWERED'
  | 'SUPPORT_QUESTION_CREATED'
  | 'OFFICE_HOURS_REMINDER'
  | 'COMMUNITY_EVENT'
  | 'ANNOUNCEMENT'

export type InboxNotification = {
  id: string
  userId: string
  kind: NotificationKind | string
  filterBucket: 'MENTIONS' | 'ANNOUNCEMENTS' | 'OTHER' | string
  title: string
  bodyPreview: string | null
  courseLabel: string | null
  href: string
  sourceType: string
  sourceId: string
  studyServerId: string | null
  courseId: string | null
  cohortId: string | null
  channelId: string | null
  createdAt: string
  readAt: string | null
  doneAt: string | null
  unread: boolean
}

export type NotificationListResponse = {
  notifications: InboxNotification[]
}

export type UnreadCountResponse = {
  unreadCount: number
}
