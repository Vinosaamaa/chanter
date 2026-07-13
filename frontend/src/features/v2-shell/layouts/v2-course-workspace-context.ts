import { createContext, useContext } from 'react'

import type { ShellCourse } from '../../shell/types'

export type V2CourseWorkspaceContextValue = {
  serverId: string
  courseId: string
  serverName: string
  course: ShellCourse
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
