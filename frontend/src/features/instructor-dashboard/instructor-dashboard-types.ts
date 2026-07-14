export type SaasPlanTier = 'STARTER' | 'PRO' | 'ORGANIZATION'

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

export const SAAS_PLAN_TIERS: SaasPlanTier[] = ['STARTER', 'PRO', 'ORGANIZATION']

export const SAAS_PLAN_LABELS: Record<SaasPlanTier, string> = {
  STARTER: 'Starter',
  PRO: 'Pro',
  ORGANIZATION: 'Organization',
}
