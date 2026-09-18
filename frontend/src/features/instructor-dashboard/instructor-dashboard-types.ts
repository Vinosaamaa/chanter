export type SaasPlanTier = 'FREE_BETA'

export type InstructorDashboard = {
  studyServerId: string
  planTier: string
  unansweredSupportQuestions: number
  repeatedQuestionGroups: number
  approvedFaqCount: number
  openTaQueueItems: number
  liveOfficeHoursSessions: number
  scheduledOfficeHoursSessions: number
  officeHoursWaitlistEntries: number
  aiInvocationCount: number
  aiInvocationLimit: number
  remainingAiInvocations: number
  quotaExhausted: boolean
  lowConfidenceHandoffs: number
  courses: TeachingCourseSummary[]
}

export type TeachingCourseSummary = {
  courseId: string
  title: string
  questionChannelId: string | null
  cohorts: TeachingCohortSummary[]
  unansweredSupportQuestions: number
  repeatedQuestionGroups: number
  approvedFaqCount: number
  openTaQueueItems: number
}

export type TeachingCohortSummary = {
  cohortId: string
  name: string
  openTaQueueItems: number
}

export type SaasPlan = {
  studyServerId: string
  planTier: SaasPlanTier
  aiInvocationLimit: number
  entitlementSource: 'OPERATOR_POLICY'
  usageWindow: 'LIFETIME'
}

export type StudyServerDetails = {
  id: string
  name: string
  ownerRole: {
    userId: string
    role: string
  }
  planTier: string
}

