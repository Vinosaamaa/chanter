export type SupportQuestionStatus = 'UNANSWERED' | 'AI_ANSWERED' | 'AI_LOW_CONFIDENCE'

export type SupportQuestion = {
  id: string
  channelMessageId: string
  channelId: string
  senderUserId: string
  body: string
  status: SupportQuestionStatus
  idempotencyKey: string
  createdAt: string
}

export type SupportQuestionSummary = {
  id: string
  channelMessageId: string
  channelId: string
  senderUserId: string
  body: string
  status: SupportQuestionStatus
  createdAt: string
}

export type SupportQuestionListResponse = {
  supportQuestions: SupportQuestionSummary[]
}

export type AssistantAnswerSource = {
  resourceId: string
  resourceTitle: string
  excerpt: string
}

export type AssistantAnswer = {
  id: string
  supportQuestionId: string
  channelId: string
  studyServerId: string
  learnerUserId: string
  questionBody: string
  answerBody: string
  confidence: 'HIGH' | 'LOW'
  handoffRecommended: boolean
  supportQuestionStatus: SupportQuestionStatus
  sources: AssistantAnswerSource[]
  createdAt: string
}

export type StudyAssistantGrant = {
  grantType: string
  grantTargetId: string
}

export type StudyAssistantPresence = {
  studyServerId: string
  installed: boolean
  grants: StudyAssistantGrant[]
}

export type TaQueueItem = {
  id: string
  cohortId: string
  supportQuestionId: string
  channelId: string
  learnerUserId: string
  body: string
  status: string
  assignedTaUserId?: string | null
  createdAt: string
  updatedAt: string
}
