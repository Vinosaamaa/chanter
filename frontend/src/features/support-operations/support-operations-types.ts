import type { TaQueueItem } from '../questions/support-question-types'

export type SupportOperation = 'ta-queue' | 'office-hours' | 'faq-approval'

export type CohortTaQueueAccess = {
  cohortId: string
  courseId: string
  studyServerId: string
  canAddToTaQueue: boolean
  canManageTaQueue: boolean
}

export type CohortOfficeHoursAccess = {
  cohortId: string
  courseId: string
  studyServerId: string
  canScheduleOfficeHours: boolean
  canJoinOfficeHours: boolean
  canManageOfficeHours: boolean
}

export type TaQueueListResponse = {
  taQueueItems: TaQueueItem[]
}

export type OfficeHoursSession = {
  id: string
  cohortId: string
  voiceChannelId: string
  scheduledByUserId: string
  startsAt: string
  endsAt: string
  status: string
  createdAt: string
}

export type OfficeHoursSessionListResponse = {
  officeHoursSessions: OfficeHoursSession[]
}

export type OfficeHoursWaitlistEntry = {
  sessionId: string
  learnerUserId: string
  joinedAt: string
  status: string
}

export type OfficeHoursWaitlistListResponse = {
  waitlistEntries: OfficeHoursWaitlistEntry[]
}

export type FaqCandidateGroup = {
  representativeQuestion: string
  supportQuestions: {
    id: string
    channelMessageId: string
    channelId: string
    senderUserId: string
    body: string
    status: string
    createdAt: string
  }[]
}

export type FaqCandidateListResponse = {
  faqCandidates: FaqCandidateGroup[]
}

export type ApprovedFaq = {
  id: string
  courseId: string
  question: string
  answer: string
  approvedByUserId: string
  createdAt: string
  updatedAt: string
}

export type ApprovedFaqListResponse = {
  approvedFaqs: ApprovedFaq[]
}
