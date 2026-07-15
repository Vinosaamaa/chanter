export type CalendarItemType = 'OFFICE_HOURS' | 'EVENT' | 'DEADLINE'
export type CalendarActionKind = 'JOIN' | 'RSVP' | 'OPEN'
export type CalendarRsvpStatus = 'GOING' | 'INTERESTED' | 'NOT_GOING'

export type CalendarItem = {
  id: string
  type: CalendarItemType
  title: string
  contextLabel: string
  startsAt: string
  endsAt: string
  href: string
  actionLabel: string | null
  actionKind: CalendarActionKind | null
  viewerRsvp: CalendarRsvpStatus | null
  studyServerId: string
  courseId: string | null
  cohortId: string | null
  sourceId: string
}

export type CalendarResponse = {
  items: CalendarItem[]
  notes: string[]
}

export type CalendarQuery = {
  from: string
  to: string
  types?: string
  search?: string
}
