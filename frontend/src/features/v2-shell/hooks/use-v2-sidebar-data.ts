import { useQueries } from '@tanstack/react-query'

import { fetchStudyServerNavigation } from '../../shell/shell-api'
import type { ShellCourse, StudyServerSummary } from '../../shell/types'
import { useAccessibleStudyServersQuery } from '../../shell/hooks/use-shell-queries'

import { courseAccentGradient } from '../course-accent'

export type V2SidebarCourse = {
  id: string
  serverId: string
  serverName: string
  title: string
  cohortLabel: string
  accentColor: string
}

export type V2SidebarServerGroup = {
  id: string
  name: string
  courses: V2SidebarCourse[]
  expanded: boolean
}

export type V2SidebarData = {
  isLoading: boolean
  isError: boolean
  showTeachingNav: boolean
  showBillingNav: boolean
  serverGroups: V2SidebarServerGroup[]
  allCourses: V2SidebarCourse[]
}

function mapCourse(
  server: StudyServerSummary,
  course: ShellCourse,
  index: number,
): V2SidebarCourse {
  const cohortLabel = course.cohorts[0]?.name ?? 'Cohort'
  return {
    id: course.id,
    serverId: server.id,
    serverName: server.name,
    title: course.title,
    cohortLabel,
    accentColor: courseAccentGradient(course.id, index).color,
  }
}

export function useV2SidebarData(activeServerId?: string): V2SidebarData {
  const serversQuery = useAccessibleStudyServersQuery()
  const servers = serversQuery.data ?? []

  const navigationQueries = useQueries({
    queries: servers.map((server) => ({
      queryKey: ['study-server-navigation', server.id],
      queryFn: () => fetchStudyServerNavigation(server.id),
      enabled: servers.length > 0,
    })),
  })

  const isLoading = serversQuery.isLoading || navigationQueries.some((query) => query.isLoading)
  const isError = serversQuery.isError || navigationQueries.some((query) => query.isError)

  const serverGroups: V2SidebarServerGroup[] = servers
    .map((server, index) => {
      const navigation = navigationQueries[index]?.data
      const courses = (navigation?.courses ?? []).map((course, courseIndex) =>
        mapCourse(server, course, courseIndex),
      )

      return {
        id: server.id,
        name: server.name.toUpperCase(),
        courses,
        expanded: activeServerId ? server.id === activeServerId : index === 0,
      }
    })
    .filter((group) => group.courses.length > 0)

  const allCourses = serverGroups.flatMap((group) => group.courses)

  return {
    isLoading,
    isError,
    showTeachingNav: navigationQueries.some((query) => query.data?.capabilities.canTeach),
    showBillingNav: navigationQueries.some((query) => query.data?.capabilities.canManageBilling),
    serverGroups,
    allCourses,
  }
}
