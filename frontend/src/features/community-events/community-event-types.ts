export type CommunityEventVisibility = 'HUB' | 'COURSE' | 'COHORT'
export type CommunityEventStatus = 'SCHEDULED' | 'CANCELLED'
export type CommunityEventRsvpStatus = 'GOING' | 'INTERESTED' | 'NOT_GOING'
export type CommunityEventFilter = 'UPCOMING' | 'PAST' | 'GOING'

export type CommunityEvent = {
  id: string
  studyServerId: string
  title: string
  description: string | null
  location: string | null
  startsAt: string
  endsAt: string
  capacity: number | null
  visibility: CommunityEventVisibility
  courseId: string | null
  cohortId: string | null
  createdByUserId: string
  status: CommunityEventStatus
  goingCount: number
  interestedCount: number
  viewerRsvp: CommunityEventRsvpStatus | null
  canEdit: boolean
  sharePath: string
  calendarPath: string
  icsPath: string
}

export type CommunityEventListResponse = {
  events: CommunityEvent[]
}

export type CreateCommunityEventInput = {
  title: string
  description?: string
  location?: string
  startsAt: string
  endsAt: string
  capacity?: number
  visibility: CommunityEventVisibility
  courseId?: string
  cohortId?: string
}
