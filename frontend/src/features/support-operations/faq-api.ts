import { apiFetch } from '../../lib/api-client'

import type { ApiFetchInit } from '../../lib/api-client'

import type {
  ApprovedFaq,
  ApprovedFaqListResponse,
  FaqCandidateListResponse,
} from './support-operations-types'

export async function listFaqCandidates(
  channelId: string,
): Promise<FaqCandidateListResponse> {
  return apiFetch<FaqCandidateListResponse>(
    `/api/v1/course-channels/${channelId}/faq-candidates`,
  )
}

export async function upsertApprovedFaq(
  courseId: string,
  payload: {
    channelId: string
    id?: string
    question: string
    answer: string
    sourceSupportQuestionIds: string[]
  },
): Promise<ApprovedFaq> {
  return apiFetch<ApprovedFaq>(`/api/v1/courses/${courseId}/approved-faqs`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function listApprovedFaqs(
  courseId: string,
  init?: ApiFetchInit,
): Promise<ApprovedFaqListResponse> {
  return apiFetch<ApprovedFaqListResponse>(
    `/api/v1/courses/${courseId}/approved-faqs`,
    init,
  )
}
