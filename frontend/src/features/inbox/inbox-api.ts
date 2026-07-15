import { apiFetch } from '../../lib/api-client'

import type {
  InboxNotification,
  NotificationFilter,
  NotificationListResponse,
  NotificationStatus,
  UnreadCountResponse,
} from './types'

export function notificationsQueryKey(filter: NotificationFilter, status: NotificationStatus) {
  return ['notifications', filter, status] as const
}

export function unreadNotificationCountQueryKey() {
  return ['notifications', 'unread-count'] as const
}

export function fetchNotifications(
  filter: NotificationFilter = 'ALL',
  status: NotificationStatus = 'OPEN',
): Promise<NotificationListResponse> {
  const params = new URLSearchParams({ filter, status })
  return apiFetch<NotificationListResponse>(`/api/v1/me/notifications?${params}`)
}

export function fetchUnreadNotificationCount(): Promise<UnreadCountResponse> {
  return apiFetch<UnreadCountResponse>('/api/v1/me/notifications/unread-count')
}

export function markNotificationRead(notificationId: string): Promise<InboxNotification> {
  return apiFetch<InboxNotification>(
    `/api/v1/me/notifications/${encodeURIComponent(notificationId)}/read`,
    { method: 'POST' },
  )
}

export function markNotificationDone(notificationId: string): Promise<InboxNotification> {
  return apiFetch<InboxNotification>(
    `/api/v1/me/notifications/${encodeURIComponent(notificationId)}/done`,
    { method: 'POST' },
  )
}
