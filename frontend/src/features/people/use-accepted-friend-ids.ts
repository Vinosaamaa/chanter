import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'

import { fetchFriends } from '../friends/friends-api'

export function useAcceptedFriendIds() {
  const query = useQuery({ queryKey: ['friends'], queryFn: fetchFriends })
  const friendIds = useMemo(
    () => new Set(query.data?.friends.map((friend) => friend.friendUserId) ?? []),
    [query.data],
  )
  return { friendIds, isLoading: query.isLoading }
}
