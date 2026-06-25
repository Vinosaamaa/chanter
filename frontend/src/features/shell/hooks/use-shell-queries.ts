import { useQuery } from '@tanstack/react-query'

import { fetchAccessibleStudyServers, fetchStudyServerNavigation } from '../shell-api'

export function useAccessibleStudyServersQuery() {
  return useQuery({
    queryKey: ['study-servers'],
    queryFn: fetchAccessibleStudyServers,
  })
}

export function useStudyServerNavigationQuery(studyServerId: string | undefined) {
  return useQuery({
    queryKey: ['study-server-navigation', studyServerId],
    queryFn: () => fetchStudyServerNavigation(studyServerId!),
    enabled: Boolean(studyServerId),
  })
}
