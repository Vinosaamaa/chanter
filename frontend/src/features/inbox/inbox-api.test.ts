import { beforeEach, describe, expect, it, vi } from 'vitest'

import { apiFetch } from '../../lib/api-client'
import {
  fetchNotifications,
  fetchUnreadNotificationCount,
  markNotificationDone,
  markNotificationRead,
} from './inbox-api'

vi.mock('../../lib/api-client', () => ({
  apiFetch: vi.fn(),
}))

describe('inbox-api', () => {
  beforeEach(() => {
    vi.mocked(apiFetch).mockReset()
  })

  it('lists notifications with filter and status', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ notifications: [] })
    await fetchNotifications('MENTIONS', 'OPEN')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/me/notifications?filter=MENTIONS&status=OPEN')
  })

  it('fetches unread count and marks read/done', async () => {
    vi.mocked(apiFetch).mockResolvedValue({ unreadCount: 2 })
    await fetchUnreadNotificationCount()
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/me/notifications/unread-count')

    vi.mocked(apiFetch).mockResolvedValue({ id: 'n1' })
    await markNotificationRead('n1')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/me/notifications/n1/read', { method: 'POST' })

    await markNotificationDone('n1')
    expect(apiFetch).toHaveBeenCalledWith('/api/v1/me/notifications/n1/done', { method: 'POST' })
  })
})
