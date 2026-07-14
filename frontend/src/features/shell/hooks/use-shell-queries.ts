import { useQuery } from '@tanstack/react-query'

import { useAuthStore } from '../../../stores/auth-store'
import { fetchAccessibleStudyServers, fetchStudyServerNavigation } from '../shell-api'

export const accessibleStudyServersQueryKey = (userId: string | undefined) =>
  ['study-servers', userId ?? 'anonymous'] as const

export const studyServerNavigationQueryKey = (
  userId: string | undefined,
  studyServerId: string | undefined,
) => ['study-server-navigation', userId ?? 'anonymous', studyServerId ?? 'none'] as const

export function useAccessibleStudyServersQuery() {
  const userId = useAuthStore((state) => state.user?.id)
  return useQuery({
    queryKey: accessibleStudyServersQueryKey(userId),
    queryFn: fetchAccessibleStudyServers,
    enabled: Boolean(userId),
  })
}

export function useStudyServerNavigationQuery(studyServerId: string | undefined) {
  const userId = useAuthStore((state) => state.user?.id)
  return useQuery({
    queryKey: studyServerNavigationQueryKey(userId, studyServerId),
    queryFn: () => fetchStudyServerNavigation(studyServerId!),
    enabled: Boolean(userId && studyServerId),
  })
}
