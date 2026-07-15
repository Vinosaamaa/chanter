export type CourseLifecycleCohort = {
  id: string
  name: string
}

export type CourseLifecycleChannel = {
  id: string
  name: string
  kind: 'TEXT' | 'VOICE'
}

export type CourseLifecycle = {
  id: string
  title: string
  description: string | null
  published: boolean
  archived: boolean
  instructorRole: {
    userId: string
    role: string
  } | null
  cohort: CourseLifecycleCohort | null
  channels: CourseLifecycleChannel[] | null
}

export type StudyServerInvitation = {
  id: string
  studyServerId: string
  studyServerName: string
  email: string
}
