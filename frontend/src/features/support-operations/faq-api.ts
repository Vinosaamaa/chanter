import { apiFetch } from '../../lib/api-client'

import type { ApiFetchInit } from '../../lib/api-client'

import type {
  ApprovedFaq,
  ApprovedFaqListResponse,
  FaqCandidateListResponse,
} from './support-operations-types'

export async function listFaqCandidates(
  channelId: string,
  viewerUserId: string,
): Promise<FaqCandidateListResponse> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<FaqCandidateListResponse>(
    `/api/v1/course-channels/${channelId}/faq-candidates?${params.toString()}`,
  )
}

export async function upsertApprovedFaq(
  courseId: string,
  payload: {
    channelId: string
    approvedByUserId: string
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
  viewerUserId: string,
  init?: ApiFetchInit,
): Promise<ApprovedFaqListResponse> {
  const params = new URLSearchParams({ viewerUserId })
  return apiFetch<ApprovedFaqListResponse>(
    `/api/v1/courses/${courseId}/approved-faqs?${params.toString()}`,
    init,
  )
}
