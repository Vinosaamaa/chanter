import { Outlet, useParams } from 'react-router-dom'

import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import type { ShellCourse } from '../../shell/types'
import { V2CourseChrome } from '../components/V2CourseChrome'
import { V2CourseWorkspaceContext, type V2CourseWorkspaceContextValue } from './v2-course-workspace-context'

const fallbackCourse: ShellCourse = {
  id: 'course-demo',
  title: 'CS 101 — Intro to Computer Science',
  cohorts: [{ id: 'cohort-demo', name: 'Spring cohort' }],
  channels: [
    { id: 'general-demo', name: 'general', kind: 'TEXT' },
    { id: 'study-group-demo', name: 'study-group', kind: 'TEXT' },
    { id: 'questions-demo', name: 'questions', kind: 'TEXT' },
    { id: 'resources-demo', name: 'resources', kind: 'TEXT' },
    { id: 'voice-demo', name: 'Study Room', kind: 'VOICE' },
  ],
}

export function V2CourseWorkspaceLayout() {
  const { serverId = 'server-demo', courseId = 'course-demo' } = useParams()
  const navigation = useStudyServerNavigationQuery(serverId)
  const course = navigation.data?.courses.find((item) => item.id === courseId) ?? fallbackCourse
  const value: V2CourseWorkspaceContextValue = {
    serverId,
    courseId,
    serverName: navigation.data?.studyServerName ?? 'Spring Bootcamp Hub',
    course,
    isOwner: navigation.data?.canViewFullCatalog ?? false,
    isLoading: navigation.isLoading,
    isError: navigation.isError,
  }

  return (
    <V2CourseWorkspaceContext.Provider value={value}>
      <section className="v2-workspace-page course-workspace-page">
        <V2CourseChrome context={value} />
        <div className="course-tab-panel">
          <Outlet />
        </div>
      </section>
    </V2CourseWorkspaceContext.Provider>
  )
}
