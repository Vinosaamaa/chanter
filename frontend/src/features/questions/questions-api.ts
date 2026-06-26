import { apiFetch } from '../../lib/api-client'

import type {
  AssistantAnswer,
  StudyAssistantPresence,
  SupportQuestion,
  SupportQuestionListResponse,
  TaQueueItem,
} from './support-question-types'

export async function postSupportQuestion(
  channelId: string,
  senderUserId: string,
  body: string,
  idempotencyKey: string,
): Promise<SupportQuestion> {
  return apiFetch<SupportQuestion>(`/api/v1/course-channels/${channelId}/support-questions`, {
    method: 'POST',
    body: JSON.stringify({ senderUserId, body, idempotencyKey }),
  })
}

export async function listUnansweredSupportQuestions(
  channelId: string,
  viewerUserId: string,
): Promise<SupportQuestionListResponse> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<SupportQuestionListResponse>(
    `/api/v1/course-channels/${channelId}/support-questions?${params.toString()}`,
  )
}

export async function invokeAssistantAnswer(
  channelId: string,
  supportQuestionId: string,
  learnerUserId: string,
): Promise<AssistantAnswer> {
  return apiFetch<AssistantAnswer>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/assistant-answer`,
    {
      method: 'POST',
      body: JSON.stringify({ learnerUserId }),
    },
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
  learnerUserId: string,
  supportQuestionId: string,
  channelId: string,
): Promise<TaQueueItem> {
  return apiFetch<TaQueueItem>(`/api/v1/cohorts/${cohortId}/ta-queue`, {
    method: 'POST',
    body: JSON.stringify({ learnerUserId, supportQuestionId, channelId }),
  })
}

export function quotaExhaustedMessage(body: string | undefined): string {
  if (body && body.trim().length > 0) {
    return body
  }
  return 'AI Study Assistant quota exhausted. Upgrade the Study Server SaaS Plan to continue.'
}
