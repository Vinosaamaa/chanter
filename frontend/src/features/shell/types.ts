export type ShellChannel = {
  id: string
  name: string
  kind: 'TEXT' | 'VOICE'
}

export type ShellCohort = {
  id: string
  name: string
}

export type ShellCourse = {
  id: string
  title: string
  cohorts: ShellCohort[]
  channels: ShellChannel[]
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
  studyServerChannels: ShellChannel[]
  courses: ShellCourse[]
}

export type SelectedChannel =
  | { scope: 'study'; channelId: string }
  | { scope: 'course'; channelId: string }
