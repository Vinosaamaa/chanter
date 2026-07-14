import { apiFetch } from '../../lib/api-client'

import type {
  AssistantAnswer,
  StudyAssistantPresence,
  SupportQuestion,
  SupportQuestionListResponse,
  SupportQuestionModerationStatus,
  SupportQuestionReply,
  SupportQuestionReplyListResponse,
  TaQueueItem,
} from './support-question-types'

export async function postSupportQuestion(
  channelId: string,
  body: string,
  idempotencyKey: string,
): Promise<SupportQuestion> {
  return apiFetch<SupportQuestion>(`/api/v1/course-channels/${channelId}/support-questions`, {
    method: 'POST',
    body: JSON.stringify({ body, idempotencyKey }),
  })
}

export async function listSupportQuestions(
  channelId: string,
): Promise<SupportQuestionListResponse> {
  return apiFetch<SupportQuestionListResponse>(
    `/api/v1/course-channels/${channelId}/support-questions`,
  )
}

export async function invokeAssistantAnswer(
  channelId: string,
  supportQuestionId: string,
): Promise<AssistantAnswer> {
  return apiFetch<AssistantAnswer>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/assistant-answer`,
    {
      method: 'POST',
    },
  )
}

export async function fetchAssistantAnswer(
  channelId: string,
  supportQuestionId: string,
): Promise<AssistantAnswer> {
  return apiFetch<AssistantAnswer>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/assistant-answer`,
  )
}

export async function listSupportQuestionReplies(
  channelId: string,
  supportQuestionId: string,
): Promise<SupportQuestionReplyListResponse> {
  return apiFetch<SupportQuestionReplyListResponse>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/replies`,
  )
}

export async function postSupportQuestionReply(
  channelId: string,
  supportQuestionId: string,
  body: string,
): Promise<SupportQuestionReply> {
  return apiFetch<SupportQuestionReply>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/replies`,
    { method: 'POST', body: JSON.stringify({ body }) },
  )
}

export async function moderateSupportQuestion(
  channelId: string,
  supportQuestionId: string,
  status: SupportQuestionModerationStatus,
): Promise<SupportQuestion> {
  return apiFetch<SupportQuestion>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/moderation`,
    { method: 'PATCH', body: JSON.stringify({ status }) },
  )
}

export async function fetchStudyAssistantPresence(
  studyServerId: string,
  viewerUserId: string,
): Promise<StudyAssistantPresence> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<StudyAssistantPresence>(
    `/api/v1/study-servers/${studyServerId}/study-assistant?${params.toString()}`,
  )
}

export async function addSupportQuestionToTaQueue(
  cohortId: string,
  supportQuestionId: string,
  channelId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue`, {
    method: 'POST',
    body: JSON.stringify({ supportQuestionId, channelId }),
  })
}

export function quotaExhaustedMessage(body: string | undefined): string {
  if (body && body.trim().length > 0) {
    return body
  }
  return 'AI Study Assistant quota exhausted. Upgrade the Study Server SaaS Plan to continue.'
}
