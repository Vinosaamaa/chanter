export type SupportQuestionStatus =
  | 'UNANSWERED'
  | 'AI_ANSWERED'
  | 'AI_LOW_CONFIDENCE'
  | 'HUMAN_ANSWERED'
  | 'RESOLVED'
  | 'CANCELLED'
  | 'DUPLICATE'

export type SupportQuestionModerationStatus = 'RESOLVED' | 'CANCELLED' | 'DUPLICATE'

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

export type SupportQuestionReply = {
  id: string
  supportQuestionId: string
  authorUserId: string
  body: string
  createdAt: string
}

export type SupportQuestionReplyListResponse = {
  replies: SupportQuestionReply[]
}

export type AssistantAnswerSource = {
  resourceId: string
  resourceTitle: string
  excerpt: string
}

export type AssistantAnswerAudit = {
  invocationType: string
  sourceCount: number
  llmUsed: boolean
  llmProvider?: string | null
  llmModel?: string | null
  createdAt: string
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
  audit?: AssistantAnswerAudit | null
  helpfulMarked?: boolean
  helpfulCount?: number
}

export type AssistantStreamPhase = 'idle' | 'streaming' | 'complete' | 'error'

export type AssistantAnswerMode = 'source-only' | 'quoted-evidence' | 'grounded-explanation'

export type AssistantAnswerSelection = {
  modelId: string
  answerMode: AssistantAnswerMode
}

export type AssistantModelCatalog = {
  defaultModelId: string
  models: {
    id: string
    label: string
    provider: string
    model: string
    mode: 'sources' | 'local' | 'api'
    billing: string
  }[]
  answerModes: {
    id: AssistantAnswerMode
    label: string
    available: boolean
    unavailableReason: string | null
  }[]
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
