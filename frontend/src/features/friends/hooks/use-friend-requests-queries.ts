import { useQuery } from '@tanstack/react-query'

import { fetchFriendRequests } from '../friends-api'

export const friendRequestsQueryKey = ['friend-requests'] as const

export function useFriendRequestsQuery() {
  return useQuery({
    queryKey: friendRequestsQueryKey,
    queryFn: fetchFriendRequests,
  })
}

export function usePendingFriendRequestCount() {
  const query = useFriendRequestsQuery()
  const incomingCount = query.data?.incoming.length ?? 0

  return {
    ...query,
    incomingCount,
  }
}
