import { act, cleanup, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { afterEach, expect, it, vi } from 'vitest'
import * as api from '../inbox-api'
import type { InboxNotification } from '../types'
import { useNotificationsQuery } from './use-inbox-queries'

vi.mock('../../../stores/auth-store', () => ({ useAuthStore: () => 'member-1' }))
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks() })

it('receives a notification delivered after the mounted Inbox first loads', async () => {
  vi.useFakeTimers()
  const delivered: InboxNotification = {
    id: 'delayed-announcement', userId: 'member-1', kind: 'ANNOUNCEMENT',
    filterBucket: 'ANNOUNCEMENTS', title: 'New announcement', bodyPreview: 'A new update.',
    courseLabel: null, href: '/app/inbox', sourceType: 'ANNOUNCEMENT', sourceId: 'source-1',
    studyServerId: 'server-1', courseId: null, cohortId: null, channelId: null,
    createdAt: '2026-09-22T20:00:00Z', readAt: null, doneAt: null, unread: true,
  }
  const fetch = vi.spyOn(api, 'fetchNotifications')
    .mockResolvedValueOnce({ notifications: [] })
    .mockResolvedValue({ notifications: [delivered] })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  const { result } = renderHook(() => useNotificationsQuery(), {
    wrapper: ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>,
  })
  await act(async () => { await vi.advanceTimersByTimeAsync(1) })
  expect(result.current.data?.notifications).toEqual([])
  await act(async () => { await vi.advanceTimersByTimeAsync(15_001) })
  expect(fetch).toHaveBeenCalledTimes(2)
  expect(result.current.data?.notifications).toEqual([delivered])
})
