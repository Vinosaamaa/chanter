import { createContext, useContext } from 'react'

import type { AssistantAnswer } from '../../questions/support-question-types'

export type QuestionsPanelContextValue = {
  studyServerId: string | null
  setStudyServerId: (studyServerId: string | null) => void
  selectedAnswer: AssistantAnswer | null
  setSelectedAnswer: (answer: AssistantAnswer | null) => void
}

export const QuestionsPanelContext = createContext<QuestionsPanelContextValue | null>(null)

export function useQuestionsPanel(): QuestionsPanelContextValue {
  const context = useContext(QuestionsPanelContext)
  if (!context) {
    throw new Error('useQuestionsPanel must be used within QuestionsPanelProvider')
  }
  return context
}
