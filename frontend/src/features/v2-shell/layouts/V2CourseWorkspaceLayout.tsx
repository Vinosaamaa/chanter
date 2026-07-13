import { Outlet, useParams } from 'react-router-dom'

import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import { V2CourseChrome } from '../components/V2CourseChrome'
import { V2CourseWorkspaceContext, type V2CourseWorkspaceContextValue } from './v2-course-workspace-context'

export function V2CourseWorkspaceLayout() {
  const { serverId, courseId } = useParams()
  const navigation = useStudyServerNavigationQuery(serverId)

  if (navigation.isLoading) {
    return <CourseWorkspaceState message="Loading course…" />
  }

  if (navigation.isError) {
    return <CourseWorkspaceState title="Unable to load course" message="Refresh the page to try again." tone="error" />
  }

  const navigationData = navigation.data
  const course = navigationData?.courses.find((item) => item.id === courseId)
  if (!serverId || !courseId || !navigationData || !course) {
    return <CourseWorkspaceState title="Course not found" message="This course is unavailable or you no longer have access." />
  }

  const value: V2CourseWorkspaceContextValue = {
    serverId,
    courseId,
    serverName: navigationData.studyServerName,
    course,
    isOwner: navigationData.canViewFullCatalog,
    isLoading: false,
    isError: false,
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

function CourseWorkspaceState({ title, message, tone }: { title?: string; message: string; tone?: 'error' }) {
  return (
    <section className={`v2-workspace-page course-workspace-state${tone ? ` ${tone}` : ''}`} role="status">
      {title ? <h1>{title}</h1> : null}
      <p>{message}</p>
    </section>
  )
}
