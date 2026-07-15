export type HomeSummaryCourse = {
  courseId: string
  studyServerId: string
  title: string
  cohortId: string | null
  cohortName: string | null
  instructorDisplayName: string | null
  progress: number | null
  progressUnavailableReason: string | null
  href: string
}

export type HomeSummaryAttentionKind = 'OFFICE_HOURS' | 'ANNOUNCEMENTS' | 'EVENT'

export type HomeSummaryAttentionItem = {
  id: string
  kind: HomeSummaryAttentionKind | string
  headline: string
  suffix?: string | null
  suffixOnNewLine?: boolean
  actionLabel?: string | null
  actionVariant?: 'button' | 'link' | string | null
  href?: string | null
  startsAt?: string | null
}

export type HomeSummaryUpNextKind = 'OFFICE_HOURS' | 'EVENT' | 'STUDY_ROOM'

export type HomeSummaryUpNextItem = {
  id: string
  kind: HomeSummaryUpNextKind | string
  title: string
  suffix?: string | null
  detail: string
  actionLabel?: string | null
  href?: string | null
  startsAt?: string | null
}

export type HomeSummaryResponse = {
  courses: HomeSummaryCourse[]
  attention: HomeSummaryAttentionItem[]
  upNext: HomeSummaryUpNextItem[]
  partialFailures: string[]
}
