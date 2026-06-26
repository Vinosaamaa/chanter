import { useMemo, useState, type ReactNode } from 'react'

import type { AssistantAnswer } from '../../questions/support-question-types'

import { QuestionsPanelContext } from './use-questions-panel'

export function QuestionsPanelProvider({ children }: { children: ReactNode }) {
  const [studyServerId, setStudyServerId] = useState<string | null>(null)
  const [selectedAnswer, setSelectedAnswer] = useState<AssistantAnswer | null>(null)

  const value = useMemo(
    () => ({
      studyServerId,
      setStudyServerId,
      selectedAnswer,
      setSelectedAnswer,
    }),
    [selectedAnswer, studyServerId],
  )

  return <QuestionsPanelContext.Provider value={value}>{children}</QuestionsPanelContext.Provider>
}
