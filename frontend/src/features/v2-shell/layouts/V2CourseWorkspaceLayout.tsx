import { useEffect } from 'react'
import { Outlet, useParams, useSearchParams } from 'react-router-dom'

import { useStudyServerNavigationQuery } from '../../shell/hooks/use-shell-queries'
import type { ShellCourse, StudyServerNavigation } from '../../shell/types'
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

  return (
    <ResolvedCourseWorkspace
      serverId={serverId}
      courseId={courseId}
      navigation={navigationData}
      course={course}
    />
  )
}

function ResolvedCourseWorkspace({
  serverId,
  courseId,
  navigation,
  course,
}: {
  serverId: string
  courseId: string
  navigation: StudyServerNavigation
  course: ShellCourse
}) {
  const storageKey = `chanter:last-cohort:${serverId}:${courseId}`
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedCohortId = searchParams.get('cohort')
  const storedCohortId = window.localStorage.getItem(storageKey)
  const selectedCohortId = [requestedCohortId, storedCohortId]
    .find((candidate) => course.cohorts.some((cohort) => cohort.id === candidate))
    ?? course.cohorts[0]?.id
  const selectedCohort = course.cohorts.find((cohort) => cohort.id === selectedCohortId)
    ?? course.cohorts[0]

  useEffect(() => {
    if (selectedCohort) {
      window.localStorage.setItem(storageKey, selectedCohort.id)
    }
  }, [selectedCohort, storageKey])

  useEffect(() => {
    if (!selectedCohort || requestedCohortId === selectedCohort.id || course.cohorts.length < 2) {
      return
    }

    const nextParams = new URLSearchParams(searchParams)
    nextParams.set('cohort', selectedCohort.id)
    setSearchParams(nextParams, { replace: true })
  }, [course.cohorts.length, requestedCohortId, searchParams, selectedCohort, setSearchParams])

  const selectCohort = (cohortId: string) => {
    if (course.cohorts.some((cohort) => cohort.id === cohortId)) {
      const nextParams = new URLSearchParams(searchParams)
      nextParams.set('cohort', cohortId)
      setSearchParams(nextParams)
    }
  }

  const value: V2CourseWorkspaceContextValue = {
    serverId,
    courseId,
    serverName: navigation.studyServerName,
    course,
    studyServerCapabilities: navigation.capabilities,
    courseCapabilities: course.capabilities,
    selectedCohort,
    selectCohort,
    isOwner: navigation.capabilities.owner,
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
