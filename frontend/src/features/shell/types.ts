export type ShellChannel = {
  id: string
  cohortId?: string | null
  name: string
  kind: 'TEXT' | 'VOICE'
}

export type ShellCohort = {
  id: string
  name: string
  capabilities: CohortCapabilities
}

export type CohortCapabilities = {
  enrolled: boolean
  teachingAssistant: boolean
  canManage: boolean
}

export type ShellCourse = {
  id: string
  title: string
  capabilities: CourseCapabilities
  cohorts: ShellCohort[]
  channels: ShellChannel[]
}

export type CourseCapabilities = {
  instructor: boolean
  teachingAssistant: boolean
  enrolled: boolean
  canManageCourse: boolean
  canManageQuestions: boolean
  canApproveFaq: boolean
  canManageTaQueue: boolean
  canUploadResources: boolean
  canScheduleOfficeHours: boolean
  canManagePeople: boolean
}

export type StudyServerCapabilities = {
  owner: boolean
  canTeach: boolean
  canCreateCourse: boolean
  canManageCommunity: boolean
  canManageEvents: boolean
  canManageBilling: boolean
}

export type StudyServerSummary = {
  id: string
  name: string
  owner: boolean
  courseCount: number
  memberCount: number
}

export type StudyServerNavigation = {
  studyServerId: string
  studyServerName: string
  canViewFullCatalog: boolean
  capabilities: StudyServerCapabilities
  studyServerChannels: ShellChannel[]
  courses: ShellCourse[]
}

export type SelectedChannel =
  | { scope: 'study'; channelId: string }
  | { scope: 'course'; channelId: string }
