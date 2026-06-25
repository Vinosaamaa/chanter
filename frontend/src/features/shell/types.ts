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
}

export type StudyServerNavigation = {
  studyServerId: string
  studyServerName: string
  studyServerChannels: ShellChannel[]
  courses: ShellCourse[]
}

export type SelectedChannel =
  | { scope: 'study'; channelId: string }
  | { scope: 'course'; channelId: string }
