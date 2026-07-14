import { useQuery } from '@tanstack/react-query'

import { useAuthStore } from '../../../stores/auth-store'

import { fetchFriendRequests } from '../friends-api'

export const friendRequestsQueryKey = (userId: string | undefined) =>
  ['friend-requests', userId ?? 'anonymous'] as const

export function useFriendRequestsQuery() {
  const userId = useAuthStore((state) => state.user?.id)

  return useQuery({
    queryKey: friendRequestsQueryKey(userId),
    queryFn: fetchFriendRequests,
    enabled: Boolean(userId),
    refetchInterval: 15_000,
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
