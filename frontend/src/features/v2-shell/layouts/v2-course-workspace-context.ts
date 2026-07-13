import { createContext, useContext } from 'react'

import type { CourseCapabilities, ShellCohort, ShellCourse, StudyServerCapabilities } from '../../shell/types'

export type V2CourseWorkspaceContextValue = {
  serverId: string
  courseId: string
  serverName: string
  course: ShellCourse
  studyServerCapabilities: StudyServerCapabilities
  courseCapabilities: CourseCapabilities
  selectedCohort: ShellCohort | undefined
  selectCohort: (cohortId: string) => void
  isOwner: boolean
  isLoading: boolean
  isError: boolean
}

export const V2CourseWorkspaceContext = createContext<V2CourseWorkspaceContextValue | null>(null)

export function useV2CourseWorkspace(): V2CourseWorkspaceContextValue {
  const context = useContext(V2CourseWorkspaceContext)
  if (!context) throw new Error('useV2CourseWorkspace must be used inside V2CourseWorkspaceLayout')
  return context
}
