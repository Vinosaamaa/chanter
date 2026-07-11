export type CreatedStudyServer = {
  id: string
  name: string
  planTier?: string
}

export type CourseChannel = {
  id: string
  name: string
  kind: 'TEXT' | 'VOICE'
}

export type CreatedCourse = {
  id: string
  title: string
  cohort: {
    id: string
    name: string
  }
  channels: CourseChannel[]
}

export type CohortEnrollmentRecord = {
  learnerUserId: string
  enrolledByUserId: string
  enrolledAt: string
}
