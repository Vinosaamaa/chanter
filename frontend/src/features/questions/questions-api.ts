import { ApiError, apiFetch, apiFetchResponse, type ApiFetchInit } from '../../lib/api-client'

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

export type StreamAssistantHandlers = {
  onToken: (token: string) => void
  onComplete: (answer: AssistantAnswer) => void
  signal?: AbortSignal
}

/**
 * POST SSE stream for Ask AI (#100). Emits named events `token` then `complete`.
 */
export async function streamAssistantAnswer(
  channelId: string,
  supportQuestionId: string,
  handlers: StreamAssistantHandlers,
): Promise<void> {
  const path = `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/assistant-answer/stream`
  const init: ApiFetchInit = { method: 'POST', signal: handlers.signal }
  const response = await apiFetchResponse(path, init)

  if (!response.body) {
    throw new ApiError('Streaming response had no body', response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []

  const flushEvent = () => {
    if (dataLines.length === 0) {
      eventName = 'message'
      return
    }
    const data = dataLines.join('\n')
    dataLines = []
    const name = eventName
    eventName = 'message'
    if (name === 'token') {
      handlers.onToken(data)
      return
    }
    if (name === 'complete') {
      handlers.onComplete(JSON.parse(data) as AssistantAnswer)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split(/\r?\n/)
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      if (line === '') {
        flushEvent()
        continue
      }
      if (line.startsWith(':')) {
        continue
      }
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
        continue
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
  }
  flushEvent()
}

export async function fetchAssistantAnswer(
  channelId: string,
  supportQuestionId: string,
): Promise<AssistantAnswer> {
  return apiFetch<AssistantAnswer>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/assistant-answer`,
  )
}

export async function markAssistantAnswerHelpful(
  channelId: string,
  supportQuestionId: string,
): Promise<AssistantAnswer> {
  return apiFetch<AssistantAnswer>(
    `/api/v1/course-channels/${channelId}/support-questions/${supportQuestionId}/assistant-answer/helpful`,
    { method: 'POST' },
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
