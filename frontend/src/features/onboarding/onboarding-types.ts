export type CreatedStudyServer = {
  id: string
  name: string
  description?: string | null
  serverType?: string | null
  planTier?: string
  pendingInvitations?: Array<{ id: string; email: string }>
}

export type CourseChannel = {
  id: string
  name: string
  kind: 'TEXT' | 'VOICE'
}

export type CreatedCourse = {
  id: string
  title: string
  description?: string | null
  published?: boolean
  archived?: boolean
  cohort?: {
    id: string
    name: string
  } | null
  channels?: CourseChannel[] | null
}

export type CohortEnrollmentRecord = {
  learnerUserId: string
  enrolledByUserId: string
  enrolledAt: string
}

export type CohortEnrollmentListResult = {
  enrollments: CohortEnrollmentRecord[]
  totalCount: number
  limit: number
  offset: number
}
