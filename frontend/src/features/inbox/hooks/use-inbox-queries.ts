import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { useAuthStore } from '../../../stores/auth-store'
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markNotificationDone,
  markNotificationRead,
  notificationsQueryKey,
  unreadNotificationCountQueryKey,
} from '../inbox-api'
import type { NotificationFilter, NotificationStatus } from '../types'

export function useNotificationsQuery(
  filter: NotificationFilter = 'ALL',
  status: NotificationStatus = 'OPEN',
) {
  const userId = useAuthStore((state) => state.user?.id)
  return useQuery({
    queryKey: [...notificationsQueryKey(filter, status), userId ?? 'anonymous'],
    queryFn: () => fetchNotifications(filter, status),
    enabled: Boolean(userId),
  })
}

export function useUnreadNotificationCountQuery() {
  const userId = useAuthStore((state) => state.user?.id)
  return useQuery({
    queryKey: [...unreadNotificationCountQueryKey(), userId ?? 'anonymous'],
    queryFn: fetchUnreadNotificationCount,
    enabled: Boolean(userId),
    refetchInterval: 30_000,
  })
}

export function useMarkNotificationReadMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}

export function useMarkNotificationDoneMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: markNotificationDone,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
  })
}
