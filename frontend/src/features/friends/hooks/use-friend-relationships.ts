import { useMemo, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import { formatUserFacingApiError, isUnauthorizedApiError } from '../../../lib/format-api-error'
import { useAuthStore } from '../../../stores/auth-store'
import { buildCoMemberDirectory, profilesByUserId } from '../friend-directory'
import {
  acceptFriendRequest,
  blockUser,
  cancelFriendRequest,
  declineFriendRequest,
  fetchBlockedUsers,
  fetchCoMembers,
  fetchPublicProfiles,
  sendFriendRequest,
} from '../friends-api'
import { friendRequestsQueryKey, useFriendRequestsQuery } from './use-friend-requests-queries'
import type { CoMember, FriendRequest } from '../types'

const EMPTY_REQUESTS: FriendRequest[] = []
const EMPTY_CO_MEMBERS: CoMember[] = []
const EMPTY_USER_IDS: string[] = []

const coMembersQueryKey = (userId: string | undefined) =>
  ['friend-co-members', userId ?? 'anonymous'] as const
const blockedUsersQueryKey = (userId: string | undefined) =>
  ['friend-blocks', userId ?? 'anonymous'] as const

export function useFriendRelationships(
  friendUserIds: string[],
  onFriendsChanged: () => Promise<void>,
) {
  const queryClient = useQueryClient()
  const userId = useAuthStore((state) => state.user?.id)
  const clearSession = useAuthStore((state) => state.clearSession)
  const requestsQuery = useFriendRequestsQuery()
  const coMembersQuery = useQuery({
    queryKey: coMembersQueryKey(userId),
    queryFn: fetchCoMembers,
    enabled: Boolean(userId),
  })
  const blockedUsersQuery = useQuery({
    queryKey: blockedUsersQueryKey(userId),
    queryFn: fetchBlockedUsers,
    enabled: Boolean(userId),
  })
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyActionIds, setBusyActionIds] = useState<Set<string>>(() => new Set())

  const incoming = requestsQuery.data?.incoming ?? EMPTY_REQUESTS
  const outgoing = requestsQuery.data?.outgoing ?? EMPTY_REQUESTS
  const coMembers = coMembersQuery.data?.coMembers ?? EMPTY_CO_MEMBERS
  const blockedUserIds = blockedUsersQuery.data?.blockedUserIds ?? EMPTY_USER_IDS
  const profileUserIds = useMemo(() => {
    const ids = new Set(friendUserIds)
    for (const coMember of coMembers) ids.add(coMember.userId)
    for (const request of incoming) ids.add(request.senderUserId)
    for (const request of outgoing) ids.add(request.recipientUserId)
    for (const blockedUserId of blockedUserIds) ids.add(blockedUserId)
    return [...ids].sort()
  }, [blockedUserIds, coMembers, friendUserIds, incoming, outgoing])
  const profilesQuery = useQuery({
    queryKey: ['friend-public-profiles', userId ?? 'anonymous', ...profileUserIds],
    queryFn: () => fetchPublicProfiles(profileUserIds),
    enabled: Boolean(userId && profileUserIds.length > 0),
  })
  const profilesById = useMemo(
    () => profilesByUserId(profilesQuery.data?.profiles ?? []),
    [profilesQuery.data?.profiles],
  )
  const directoryEntries = useMemo(
    () =>
      buildCoMemberDirectory({
        coMembers,
        profilesById,
        friendUserIds: new Set(friendUserIds),
        incomingUserIds: new Set(incoming.map((request) => request.senderUserId)),
        outgoingUserIds: new Set(outgoing.map((request) => request.recipientUserId)),
        blockedUserIds: new Set(blockedUserIds),
      }),
    [blockedUserIds, coMembers, friendUserIds, incoming, outgoing, profilesById],
  )

  const refreshRequests = () =>
    queryClient.invalidateQueries({ queryKey: friendRequestsQueryKey(userId) })
  const refreshBlocks = () =>
    queryClient.invalidateQueries({ queryKey: blockedUsersQueryKey(userId) })

  const runAction = async (
    actionId: string,
    action: () => Promise<void>,
  ): Promise<boolean> => {
    setBusyActionIds((current) => new Set(current).add(actionId))
    setActionError(null)
    try {
      await action()
      return true
    } catch (caught) {
      if (isUnauthorizedApiError(caught)) {
        clearSession()
        return false
      }
      setActionError(formatUserFacingApiError(caught, 'Unable to update this friendship.'))
      return false
    } finally {
      setBusyActionIds((current) => {
        const next = new Set(current)
        next.delete(actionId)
        return next
      })
    }
  }

  return {
    profilesById,
    directoryEntries,
    incoming,
    outgoing,
    isLoading:
      requestsQuery.isLoading ||
      coMembersQuery.isLoading ||
      blockedUsersQuery.isLoading ||
      (profileUserIds.length > 0 && profilesQuery.isLoading),
    error:
      requestsQuery.isError ||
      coMembersQuery.isError ||
      blockedUsersQuery.isError ||
      profilesQuery.isError
        ? 'Could not load friend relationships.'
        : null,
    actionError,
    busyActionIds,
    sendRequest: (recipientUserId: string) =>
      runAction(recipientUserId, async () => {
        await sendFriendRequest(recipientUserId)
        await refreshRequests()
      }),
    acceptRequest: (friendRequestId: string) =>
      runAction(friendRequestId, async () => {
        await acceptFriendRequest(friendRequestId)
        await refreshRequests()
        await onFriendsChanged()
      }),
    declineRequest: (friendRequestId: string) =>
      runAction(friendRequestId, async () => {
        await declineFriendRequest(friendRequestId)
        await refreshRequests()
      }),
    cancelRequest: (friendRequestId: string) =>
      runAction(friendRequestId, async () => {
        await cancelFriendRequest(friendRequestId)
        await refreshRequests()
      }),
    blockPeer: (peerUserId: string) =>
      runAction(peerUserId, async () => {
        await blockUser(peerUserId)
        await Promise.all([refreshRequests(), refreshBlocks()])
        await onFriendsChanged()
      }),
  }
}
