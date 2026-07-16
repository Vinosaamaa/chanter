import { apiFetch } from '../../lib/api-client'
import type { StudyAssistantPresence } from '../questions/support-question-types'

import type {
  StudyAssistantGrantSelection,
  StudyAssistantInstallPreview,
} from './study-assistant-types'

export async function fetchStudyAssistantInstallPreview(
  studyServerId: string,
): Promise<StudyAssistantInstallPreview> {
  return apiFetch<StudyAssistantInstallPreview>(
    `/api/v1/study-servers/${studyServerId}/study-assistant/install-preview`,
  )
}

export async function installStudyAssistant(
  studyServerId: string,
  grants: StudyAssistantGrantSelection[],
): Promise<StudyAssistantPresence> {
  return apiFetch<StudyAssistantPresence>(
    `/api/v1/study-servers/${studyServerId}/study-assistant/install`,
    {
      method: 'POST',
      body: JSON.stringify({ grants }),
    },
  )
}

export function studyAssistantInstallErrorMessage(error: unknown): string {
  if (error instanceof Error && 'status' in error) {
    const status = (error as { status: number }).status
    if (status === 403) {
      return 'Only Study Server owners and course instructors can install the AI Study Assistant.'
    }
    if (status === 409) {
      return 'AI Study Assistant is already installed in this Study Server.'
    }
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message
  }

  return 'Unable to install the AI Study Assistant. Try again in a moment.'
}
