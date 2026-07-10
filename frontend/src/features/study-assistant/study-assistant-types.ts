export type StudyAssistantGrantType =
  | 'STUDY_SERVER_CHANNEL'
  | 'COURSE'
  | 'COHORT'
  | 'COURSE_CHANNEL'
  | 'COURSE_RESOURCE'

export type StudyAssistantGrantSelection = {
  grantType: StudyAssistantGrantType
  grantTargetId: string
}

export type StudyAssistantChannelCandidate = {
  id: string
  name: string
  kind: string
}

export type StudyAssistantCohortCandidate = {
  id: string
  name: string
}

export type StudyAssistantCourseCandidate = {
  id: string
  title: string
  cohorts: StudyAssistantCohortCandidate[]
  channels: StudyAssistantChannelCandidate[]
}

export type StudyAssistantResourceCandidate = {
  id: string
  courseId: string
  title: string
  fileName: string
  aiApproved: boolean
}

export type StudyAssistantInstallPreview = {
  studyServerId: string
  alreadyInstalled: boolean
  candidates: {
    studyServerId: string
    studyServerChannels: StudyAssistantChannelCandidate[]
    courses: StudyAssistantCourseCandidate[]
  }
  courseResources: StudyAssistantResourceCandidate[]
}
