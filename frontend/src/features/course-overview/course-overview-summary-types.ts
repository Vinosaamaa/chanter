export type CourseOverviewItemKind = 'OFFICE_HOURS' | 'STUDY_ROOM' | 'EVENT' | string

export type CourseOverviewItem = {
  id: string
  kind: CourseOverviewItemKind
  title: string
  detail: string
  actionLabel?: string | null
  href?: string | null
  startsAt?: string | null
}

export type CourseOverviewSummaryResponse = {
  progress: number | null
  progressUnavailableReason: string | null
  thisWeek: CourseOverviewItem[]
  recentActivity: CourseOverviewItem[]
  upNext: CourseOverviewItem[]
  partialFailures: string[]
}
